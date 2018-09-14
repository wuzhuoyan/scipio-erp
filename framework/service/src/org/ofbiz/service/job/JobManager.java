/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.service.job;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

import org.ofbiz.base.config.GenericConfigException;
import org.ofbiz.base.util.Assert;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityJoinOperator;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.serialize.SerializeException;
import org.ofbiz.entity.serialize.XmlSerializer;
import org.ofbiz.entity.transaction.TransactionUtil;
import org.ofbiz.entity.util.EntityListIterator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceContainer;
import org.ofbiz.service.calendar.RecurrenceInfo;
import org.ofbiz.service.calendar.RecurrenceInfoException;
import org.ofbiz.service.config.ServiceConfigUtil;
import org.ofbiz.service.config.model.RunFromPool;

import com.ibm.icu.util.Calendar;

/**
 * Job manager. The job manager queues and manages jobs. Client code can queue a job to be run immediately
 * by calling the {@link #runJob(Job)} method, or schedule a job to be run later by calling the
 * {@link #schedule(String, String, String, Map, long, int, int, int, long, int)} method.
 * Scheduled jobs are persisted in the JobSandbox entity.
 * <p>A scheduled job's start time is an approximation - the actual start time will depend
 * on the job manager/job poller configuration (poll interval) and the load on the server.
 * Scheduled jobs might be rescheduled if the server is busy. Therefore, applications
 * requiring a precise job start time should use a different mechanism to schedule the job.</p>
 */
public final class JobManager {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String instanceId = UtilProperties.getPropertyValue("general", "unique.instanceId", "ofbiz0");
    private static final ConcurrentHashMap<String, JobManager> registeredManagers = new ConcurrentHashMap<>();
    private static boolean isShutDown = false;

    private static void assertIsRunning() {
        if (isShutDown) {
            throw new IllegalStateException("Scipio shutting down");
        }
    }

    /**
     * Returns a <code>JobManager</code> instance.
     * @param delegator
     * @param enablePoller Enables polling of the JobSandbox entity.
     * @throws IllegalStateException if the Job Manager is shut down.
     */
    public static JobManager getInstance(Delegator delegator, boolean enablePoller) {
        assertIsRunning();
        Assert.notNull("delegator", delegator);
        JobManager jm = registeredManagers.get(delegator.getDelegatorName());
        if (jm == null) {
            jm = new JobManager(delegator);
            registeredManagers.putIfAbsent(delegator.getDelegatorName(), jm);
            jm = registeredManagers.get(delegator.getDelegatorName());
            if (enablePoller) {
                JobPoller.registerJobManager(jm);
            }
        }
        return jm;
    }

    /**
     * Shuts down all job managers. This method is called when OFBiz shuts down.
     */
    public static void shutDown() {
        isShutDown = true;
        JobPoller.getInstance().stop();
    }

    private final Delegator delegator;
    private boolean crashedJobsReloaded = false;

    /**
     * SCIPIO: Determines if run-at-start jobs have been queued or not.
     * <p>
     * TODO?: later might need to substitute this with a more comprehensive
     * queue or message stack ("SCH_EVENT_STARTUP", etc.).
     */
    private volatile boolean startupJobsQueued = false;

    private JobManager(Delegator delegator) {
        this.delegator = delegator;
    }

    /** Returns the Delegator. */
    public Delegator getDelegator() {
        return this.delegator;
    }

    /** Returns the LocalDispatcher. */
    public LocalDispatcher getDispatcher() {
        LocalDispatcher thisDispatcher = ServiceContainer.getLocalDispatcher(delegator.getDelegatorName(), delegator);
        return thisDispatcher;
    }

    /**
     * Get a List of each threads current state.
     *
     * @return List containing a Map of each thread's state.
     */
    public Map<String, Object> getPoolState() {
        return JobPoller.getInstance().getPoolState();
    }

    /**
     * Return true if the jobManager can run job.
     *
     * @return boolean.
     */
    public boolean isAvailable() {
        try {
            //check if a lock is enable for the period on entity JobManagerLock
            EntityCondition condition = EntityCondition.makeCondition(UtilMisc.toList(
                    EntityCondition.makeConditionDate("fromDate", "thruDate"),
                    EntityCondition.makeCondition(UtilMisc.toList(
                            EntityCondition.makeCondition("instanceId", instanceId),
                            EntityCondition.makeCondition("instanceId", "_NA_"))
                            , EntityJoinOperator.OR)
                    ), EntityJoinOperator.AND);
            return delegator.findCountByCondition("JobManagerLock", condition, null, null) == 0;
        } catch (GenericEntityException e) {
            Debug.logWarning(e, "Exception thrown while check lock on JobManager : " + instanceId, module);
            return false;
        }
    }

    private static List<String> getRunPools() throws GenericConfigException {
        List<RunFromPool> runFromPools = ServiceConfigUtil.getServiceEngine().getThreadPool().getRunFromPools();
        List<String> readPools = new ArrayList<>(runFromPools.size());
        for (RunFromPool runFromPool : runFromPools) {
            readPools.add(runFromPool.getName());
        }
        return readPools;
    }

    /**
     * Scans the JobSandbox entity and returns a list of jobs that are due to run.
     * Returns an empty list if there are no jobs due to run.
     * This method is called by the {@link JobPoller} polling thread.
     */
    protected List<Job> poll(int limit) {
        assertIsRunning();
        // The rest of this method logs exceptions and does not throw them.
        // The idea is to keep the JobPoller working even when a database
        // connection is not available (possible on a saturated server).
        DispatchContext dctx = getDispatcher().getDispatchContext();
        if (dctx == null) {
            Debug.logWarning("Unable to locate DispatchContext object; not running job!", module);
            return Collections.emptyList();
        }
        // basic query
        List<EntityExpr> expressions = UtilMisc.toList(EntityCondition.makeCondition("runTime", EntityOperator.LESS_THAN_EQUAL_TO, UtilDateTime.nowTimestamp()),
                EntityCondition.makeCondition("startDateTime", EntityOperator.EQUALS, null),
                EntityCondition.makeCondition("cancelDateTime", EntityOperator.EQUALS, null),
                EntityCondition.makeCondition("runByInstanceId", EntityOperator.EQUALS, null));
        // limit to just defined pools
        List<String> pools = null;
        try {
            pools = getRunPools();
        } catch (GenericConfigException e) {
            Debug.logWarning(e, "Unable to get run pools - not running job: ", module);
            return Collections.emptyList();
        }
        List<EntityExpr> poolsExpr = UtilMisc.toList(EntityCondition.makeCondition("poolId", EntityOperator.EQUALS, null));
        if (!pools.isEmpty()) {
            for (String poolName : pools) {
                poolsExpr.add(EntityCondition.makeCondition("poolId", EntityOperator.EQUALS, poolName));
            }
        }
        List<Job> poll = new ArrayList<>(limit);
        // make the conditions
        EntityCondition baseCondition = EntityCondition.makeCondition(expressions);
        EntityCondition poolCondition = EntityCondition.makeCondition(poolsExpr, EntityOperator.OR);
        EntityCondition mainCondition = EntityCondition.makeCondition(UtilMisc.toList(baseCondition, poolCondition));

        // SCIPIO: We must add to the main condition that the special new field eventId must be null
        EntityCondition commonCondition = mainCondition;
        mainCondition = EntityCondition.makeCondition(commonCondition, EntityCondition.makeCondition("eventId", null));

        boolean beganTransaction = false;

        // SCIPIO: first, add the run-at-startup jobs
        if (!startupJobsQueued) {
            try {
                beganTransaction = TransactionUtil.begin();
                if (!beganTransaction) {
                    Debug.logWarning("Unable to poll JobSandbox for jobs; unable to begin transaction.", module);
                    return poll;
                }

                try (EntityListIterator jobsIterator = queryStartupJobs(commonCondition)) {
                    // NOTE: due to synchronization, we could have null here
                    if (jobsIterator != null) {
                        // SCIPIO: FIXME?: We currently ignore the limit for the startup jobs;
                        // might want to find way to delay them to next poll, because we violate the limit request from caller...
                        ownAndCollectJobs(dctx, delegator, -1, jobsIterator, poll);

                        if (Debug.infoOn()) {
                            Debug.logInfo("Scipio: Collected " + poll.size() +
                                    " SCH_EVENT_STARTUP run-at-startup jobs for queuing", module);
                        }
                    }
                }
                //} catch (GenericEntityException e) { // SCIPIO: 2018-08-29: this catch is counter-productive
                //    Debug.logWarning(e, module);
                //}

                TransactionUtil.commit(beganTransaction);
            } catch (Throwable t) {
                String errMsg = "Exception thrown while polling JobSandbox: ";
                try {
                    TransactionUtil.rollback(beganTransaction, errMsg, t);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, "Exception thrown while rolling back transaction: ", module);
                }
                Debug.logWarning(t, errMsg, module);
                return Collections.emptyList();
            }
        }

        beganTransaction = false;
        try {
            beganTransaction = TransactionUtil.begin();
            if (!beganTransaction) {
                Debug.logWarning("Unable to poll JobSandbox for jobs; unable to begin transaction.", module);
                return poll;
            }

            try (EntityListIterator jobsIterator = EntityQuery.use(delegator).from("JobSandbox").where(mainCondition).orderBy("runTime").queryIterator()) {
                // SCIPIO: factored out into method
                ownAndCollectJobs(dctx, delegator, limit, jobsIterator, poll);
            }
            //} catch (GenericEntityException e) { // SCIPIO: 2018-08-29: this catch is counter-productive
            //    Debug.logWarning(e, module);
            //}

            TransactionUtil.commit(beganTransaction);
        } catch (Throwable t) {
            String errMsg = "Exception thrown while polling JobSandbox: ";
            try {
                TransactionUtil.rollback(beganTransaction, errMsg, t);
            } catch (GenericEntityException e) {
                Debug.logWarning(e, "Exception thrown while rolling back transaction: ", module);
            }
            Debug.logWarning(t, errMsg, module);
            return Collections.emptyList();
        }
        if (poll.isEmpty()) {
            // No jobs to run, see if there are any jobs to purge
            Calendar cal = Calendar.getInstance();
            try {
                int daysToKeep = ServiceConfigUtil.getServiceEngine().getThreadPool().getPurgeJobDays();
                cal.add(Calendar.DAY_OF_YEAR, -daysToKeep);
            } catch (GenericConfigException e) {
                Debug.logWarning(e, "Unable to get purge job days: ", module);
                return Collections.emptyList();
            }
            Timestamp purgeTime = new Timestamp(cal.getTimeInMillis());
            List<EntityExpr> finExp = UtilMisc.toList(EntityCondition.makeCondition("finishDateTime", EntityOperator.NOT_EQUAL, null), EntityCondition.makeCondition("finishDateTime", EntityOperator.LESS_THAN, purgeTime));
            List<EntityExpr> canExp = UtilMisc.toList(EntityCondition.makeCondition("cancelDateTime", EntityOperator.NOT_EQUAL, null), EntityCondition.makeCondition("cancelDateTime", EntityOperator.LESS_THAN, purgeTime));
            EntityCondition doneCond = EntityCondition.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(canExp), EntityCondition.makeCondition(finExp)), EntityOperator.OR);
            mainCondition = EntityCondition.makeCondition(UtilMisc.toList(EntityCondition.makeCondition("runByInstanceId", instanceId), doneCond));
            beganTransaction = false;
            try {
                beganTransaction = TransactionUtil.begin();
                if (!beganTransaction) {
                    Debug.logWarning("Unable to poll JobSandbox for jobs; unable to begin transaction.", module);
                    return Collections.emptyList();
                }
                try (EntityListIterator jobsIterator = EntityQuery.use(delegator).from("JobSandbox").where(mainCondition).orderBy("jobId").queryIterator()) {
                    GenericValue jobValue = jobsIterator.next();
                    while (jobValue != null) {
                        poll.add(new PurgeJob(jobValue));
                        if (poll.size() == limit) {
                            break;
                        }
                        jobValue = jobsIterator.next();
                    }
                }
                //} catch (GenericEntityException e) { // SCIPIO: 2018-08-29: this catch is counter-productive
                //    Debug.logWarning(e, module);
                //}
                TransactionUtil.commit(beganTransaction);
            } catch (Throwable t) {
                String errMsg = "Exception thrown while polling JobSandbox: ";
                try {
                    TransactionUtil.rollback(beganTransaction, errMsg, t);
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, "Exception thrown while rolling back transaction: ", module);
                }
                Debug.logWarning(t, errMsg, module);
                return Collections.emptyList();
            }
        }
        return poll;
    }

    /**
     * SCIPIO: Takes ownership of job and adds to list.
     * <p>
     * Factored out from {@link #poll}.
     */
    protected void ownAndCollectJobs(DispatchContext dctx, Delegator delegator, int limit,
            EntityListIterator jobsIterator, List<Job> poll) throws GenericEntityException {
        if (limit < 0 || poll.size() < limit) {
            GenericValue jobValue = jobsIterator.next();
            while (jobValue != null) {
                // Claim ownership of this value. Using storeByCondition to avoid a race condition.
                List<EntityExpr> updateExpression = UtilMisc.toList(EntityCondition.makeCondition("jobId", EntityOperator.EQUALS, jobValue.get("jobId")), EntityCondition.makeCondition("runByInstanceId", EntityOperator.EQUALS, null));
                int rowsUpdated = delegator.storeByCondition("JobSandbox", UtilMisc.toMap("runByInstanceId", instanceId), EntityCondition.makeCondition(updateExpression));
                if (rowsUpdated == 1) {
                    poll.add(new PersistedServiceJob(dctx, jobValue, null));
                    if (limit >= 0 && poll.size() == limit) { // SCIPIO: modified to support limit = -1
                        break;
                    }
                }
                jobValue = jobsIterator.next();
            }
        }
    }

    /**
     * SCIPIO: Queries run-at-start Job entities if not already done.
     * If already done, returns null.
     * @throws GenericEntityException
     */
    public synchronized EntityListIterator queryStartupJobs(EntityCondition commonCondition) throws GenericEntityException {
        EntityListIterator res = null;
        if (!startupJobsQueued) {
            res = queryStartupJobsAlways(commonCondition);
            startupJobsQueued = true;
        }
        return res;
    }

    /**
     * SCIPIO: Queries run-at-start Job entities.
     * <p>
     * TODO: If commonCondition null, build it (requires more refactor); commonCondition should be optimization only
     */
    public EntityListIterator queryStartupJobsAlways(EntityCondition commonCondition) throws GenericEntityException {
        EntityCondition mainCondition = EntityCondition.makeCondition(commonCondition, EntityCondition.makeCondition("eventId", "SCH_EVENT_STARTUP"));
        return EntityQuery.use(delegator).from("JobSandbox").where(mainCondition).orderBy("runTime").queryIterator();
    }

    public synchronized void reloadCrashedJobs() {
        assertIsRunning();
        if (crashedJobsReloaded) {
            return;
        }
        List<GenericValue> crashed = null;
        List<EntityExpr> statusExprList = UtilMisc.toList(EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "SERVICE_PENDING"),
                EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "SERVICE_QUEUED"),
                EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "SERVICE_RUNNING"));
        EntityCondition statusCondition = EntityCondition.makeCondition(statusExprList, EntityOperator.OR);
        EntityCondition mainCondition = EntityCondition.makeCondition(UtilMisc.toList(EntityCondition.makeCondition("runByInstanceId", instanceId), statusCondition));
        try {
            crashed = EntityQuery.use(delegator).from("JobSandbox").where(mainCondition).orderBy("startDateTime").queryList();
        } catch (GenericEntityException e) {
            Debug.logWarning(e, "Unable to load crashed jobs", module);
        }
        if (UtilValidate.isNotEmpty(crashed)) {
            int rescheduled = 0;
            Timestamp now = UtilDateTime.nowTimestamp();
            for (GenericValue job : crashed) {
                try {
                    if (Debug.infoOn()) {
                        Debug.logInfo("Scheduling Job : " + job, module);
                    }

                    // SCIPIO: IMPORTANT: If the job was supposed to trigger on specific event, DO NOT
                    // reschedule anything. Otherwise, we may be running services during times at
                    // which they were never meant to run and cause unexpected problems.
                    if (job.getString("eventId") == null) {
                        String pJobId = job.getString("parentJobId");
                        if (pJobId == null) {
                            pJobId = job.getString("jobId");
                        }
                        GenericValue newJob = GenericValue.create(job);
                        newJob.set("statusId", "SERVICE_PENDING");
                        newJob.set("runTime", now);
                        newJob.set("previousJobId", job.getString("jobId"));
                        newJob.set("parentJobId", pJobId);
                        newJob.set("startDateTime", null);
                        newJob.set("runByInstanceId", null);
                        //don't set a recurrent schedule on the new job, run it just one time
                        newJob.set("tempExprId", null);
                        newJob.set("recurrenceInfoId", null);
                        delegator.createSetNextSeqId(newJob);
                    } else {
                        if (Debug.infoOn()) Debug.logInfo("Scipio: Not rescheduling crashed job '" + job.getString("jobId") +
                                "' with event ID '" + job.getString("eventId") + "'", module);
                    }

                    // set the cancel time on the old job to the same as the re-schedule time
                    job.set("statusId", "SERVICE_CRASHED");
                    job.set("cancelDateTime", now);
                    delegator.store(job);
                    rescheduled++;
                } catch (GenericEntityException e) {
                    Debug.logWarning(e, module);
                }
            }
            if (Debug.infoOn()) {
                Debug.logInfo("-- " + rescheduled + " jobs re-scheduled", module);
            }
        } else {
            if (Debug.infoOn()) {
                Debug.logInfo("No crashed jobs to re-schedule", module);
            }
        }
        crashedJobsReloaded = true;
    }

    /** Queues a Job to run now.
     * @throws IllegalStateException if the Job Manager is shut down.
     * @throws RejectedExecutionException if the poller is stopped.
     */
    public void runJob(Job job) throws JobManagerException {
        assertIsRunning();
        if (job.isValid()) {
            JobPoller.getInstance().queueNow(job);
        }
    }

    /**
     * Schedule a job to start at a specific time with specific recurrence info
     *
     * @param serviceName
     *            The name of the service to invoke
     *@param context
     *            The context for the service
     *@param startTime
     *            The time in milliseconds the service should run
     *@param frequency
     *            The frequency of the recurrence (HOURLY,DAILY,MONTHLY,etc)
     *@param interval
     *            The interval of the frequency recurrence
     *@param count
     *            The number of times to repeat
     */
    public void schedule(String serviceName, Map<String, ? extends Object> context, long startTime, int frequency, int interval, int count) throws JobManagerException {
        schedule(serviceName, context, startTime, frequency, interval, count, 0);
    }

    /**
     * Schedule a job to start at a specific time with specific recurrence info
     *
     * @param serviceName
     *            The name of the service to invoke
     *@param context
     *            The context for the service
     *@param startTime
     *            The time in milliseconds the service should run
     *@param frequency
     *            The frequency of the recurrence (HOURLY,DAILY,MONTHLY,etc)
     *@param interval
     *            The interval of the frequency recurrence
     *@param count
     *            The number of times to repeat
     *@param endTime
     *            The time in milliseconds the service should expire
     */
    public void schedule(String serviceName, Map<String, ? extends Object> context, long startTime, int frequency, int interval, int count, long endTime) throws JobManagerException {
        schedule(null, serviceName, context, startTime, frequency, interval, count, endTime);
    }

    /**
     * Schedule a job to start at a specific time with specific recurrence info
     *
     * @param serviceName
     *            The name of the service to invoke
     *@param context
     *            The context for the service
     *@param startTime
     *            The time in milliseconds the service should run
     *@param frequency
     *            The frequency of the recurrence (HOURLY,DAILY,MONTHLY,etc)
     *@param interval
     *            The interval of the frequency recurrence
     *@param endTime
     *            The time in milliseconds the service should expire
     */
    public void schedule(String serviceName, Map<String, ? extends Object> context, long startTime, int frequency, int interval, long endTime) throws JobManagerException {
        schedule(serviceName, context, startTime, frequency, interval, -1, endTime);
    }

    /**
     * Schedule a job to start at a specific time with specific recurrence info
     *
     * @param poolName
     *            The name of the pool to run the service from
     *@param serviceName
     *            The name of the service to invoke
     *@param context
     *            The context for the service
     *@param startTime
     *            The time in milliseconds the service should run
     *@param frequency
     *            The frequency of the recurrence (HOURLY,DAILY,MONTHLY,etc)
     *@param interval
     *            The interval of the frequency recurrence
     *@param count
     *            The number of times to repeat
     *@param endTime
     *            The time in milliseconds the service should expire
     */
    public void schedule(String poolName, String serviceName, Map<String, ? extends Object> context, long startTime, int frequency,
            int interval, int count, long endTime) throws JobManagerException {
        schedule(null, null, serviceName, context, startTime, frequency, interval, count, endTime, -1);
    }

    /**
     * Schedule a job to start at a specific time with specific recurrence info
     *
     * @param poolName
     *            The name of the pool to run the service from
     *@param serviceName
     *            The name of the service to invoke
     *@param dataId
     *            The persisted context (RuntimeData.runtimeDataId)
     *@param startTime
     *            The time in milliseconds the service should run
     */
    public void schedule(String poolName, String serviceName, String dataId, long startTime) throws JobManagerException {
        schedule(null, poolName, serviceName, dataId, startTime, -1, 0, 1, 0, -1);
    }

    /**
     * Schedule a job to start at a specific time with specific recurrence info
     * <p>
     * SCIPIO: Modified to accept an event ID.
     *
     * @param jobName
     *            The name of the job
     *@param poolName
     *            The name of the pool to run the service from
     *@param serviceName
     *            The name of the service to invoke
     *@param context
     *            The context for the service
     *@param startTime
     *            The time in milliseconds the service should run
     *@param frequency
     *            The frequency of the recurrence (HOURLY,DAILY,MONTHLY,etc)
     *@param interval
     *            The interval of the frequency recurrence
     *@param count
     *            The number of times to repeat
     *@param endTime
     *            The time in milliseconds the service should expire
     *@param maxRetry
     *            The max number of retries on failure (-1 for no max)
     *@param eventId
     *            The triggering event
     */
    public void schedule(String jobName, String poolName, String serviceName, Map<String, ? extends Object> context, long startTime,
            int frequency, int interval, int count, long endTime, int maxRetry, String eventId) throws JobManagerException {
        // persist the context
        String dataId = null;
        try {
            GenericValue runtimeData = delegator.makeValue("RuntimeData");
            runtimeData.set("runtimeInfo", XmlSerializer.serialize(context));
            runtimeData = delegator.createSetNextSeqId(runtimeData);
            dataId = runtimeData.getString("runtimeDataId");
        } catch (GenericEntityException | SerializeException | IOException e) {
            throw new JobManagerException(e.getMessage(), e);
        }
        // schedule the job
        schedule(jobName, poolName, serviceName, dataId, startTime, frequency, interval, count, endTime, maxRetry, eventId);
    }

    /**
     * Schedule a job to start at a specific time with specific recurrence info
     * <p>
     * SCIPIO: This is now delegating.
     *
     * @param jobName
     *            The name of the job
     *@param poolName
     *            The name of the pool to run the service from
     *@param serviceName
     *            The name of the service to invoke
     *@param context
     *            The context for the service
     *@param startTime
     *            The time in milliseconds the service should run
     *@param frequency
     *            The frequency of the recurrence (HOURLY,DAILY,MONTHLY,etc)
     *@param interval
     *            The interval of the frequency recurrence
     *@param count
     *            The number of times to repeat
     *@param endTime
     *            The time in milliseconds the service should expire
     *@param maxRetry
     *            The max number of retries on failure (-1 for no max)
     */
    public void schedule(String jobName, String poolName, String serviceName, Map<String, ? extends Object> context, long startTime,
            int frequency, int interval, int count, long endTime, int maxRetry) throws JobManagerException {
        schedule(jobName, poolName, serviceName, context, startTime, frequency, interval, count, endTime, maxRetry, (String) null);
    }

    /**
     * Schedule a job to start at a specific time with specific recurrence info
     * <p>
     * SCIPIO: Modified to accept an event ID.
     *
     * @param jobName
     *            The name of the job
     *@param poolName
     *            The name of the pool to run the service from
     *@param serviceName
     *            The name of the service to invoke
     *@param dataId
     *            The persisted context (RuntimeData.runtimeDataId)
     *@param startTime
     *            The time in milliseconds the service should run
     *@param frequency
     *            The frequency of the recurrence (HOURLY,DAILY,MONTHLY,etc)
     *@param interval
     *            The interval of the frequency recurrence
     *@param count
     *            The number of times to repeat
     *@param endTime
     *            The time in milliseconds the service should expire
     *@param maxRetry
     *            The max number of retries on failure (-1 for no max)
     *@param eventId
     *            The triggering event
     * @throws IllegalStateException if the Job Manager is shut down.
     */
    public void schedule(String jobName, String poolName, String serviceName, String dataId, long startTime, int frequency, int interval,
            int count, long endTime, int maxRetry, String eventId) throws JobManagerException {
        assertIsRunning();
        // create the recurrence
        String infoId = null;
        if (frequency > -1 && count != 0) {
            try {
                RecurrenceInfo info = RecurrenceInfo.makeInfo(delegator, startTime, frequency, interval, count);
                infoId = info.primaryKey();
            } catch (RecurrenceInfoException e) {
                throw new JobManagerException(e.getMessage(), e);
            }
        }
        // set the persisted fields
        if (UtilValidate.isEmpty(jobName)) {
            jobName = Long.toString((new Date().getTime()));
        }
        // SCIPIO: now set eventId
        Map<String, Object> jFields = UtilMisc.<String, Object> toMap("jobName", jobName, "runTime", new java.sql.Timestamp(startTime),
                "serviceName", serviceName, "statusId", "SERVICE_PENDING", "recurrenceInfoId", infoId, "runtimeDataId", dataId,
                "eventId", eventId);
        // set the pool ID
        if (UtilValidate.isNotEmpty(poolName)) {
            jFields.put("poolId", poolName);
        } else {
            try {
                jFields.put("poolId", ServiceConfigUtil.getServiceEngine().getThreadPool().getSendToPool());
            } catch (GenericConfigException e) {
                throw new JobManagerException(e.getMessage(), e);
            }
        }
        // set the loader name
        jFields.put("loaderName", delegator.getDelegatorName());
        // set the max retry
        jFields.put("maxRetry", (long) maxRetry);
        jFields.put("currentRetryCount", 0L);
        // create the value and store
        GenericValue jobV;
        try {
            jobV = delegator.makeValue("JobSandbox", jFields);
            delegator.createSetNextSeqId(jobV);
        } catch (GenericEntityException e) {
            throw new JobManagerException(e.getMessage(), e);
        }
    }

    /**
     * Schedule a job to start at a specific time with specific recurrence info
     * <p>
     * SCIPIO: Now delegating.
     *
     * @param jobName
     *            The name of the job
     *@param poolName
     *            The name of the pool to run the service from
     *@param serviceName
     *            The name of the service to invoke
     *@param dataId
     *            The persisted context (RuntimeData.runtimeDataId)
     *@param startTime
     *            The time in milliseconds the service should run
     *@param frequency
     *            The frequency of the recurrence (HOURLY,DAILY,MONTHLY,etc)
     *@param interval
     *            The interval of the frequency recurrence
     *@param count
     *            The number of times to repeat
     *@param endTime
     *            The time in milliseconds the service should expire
     *@param maxRetry
     *            The max number of retries on failure (-1 for no max)
     * @throws IllegalStateException if the Job Manager is shut down.
     */
    public void schedule(String jobName, String poolName, String serviceName, String dataId, long startTime, int frequency, int interval,
            int count, long endTime, int maxRetry) throws JobManagerException {
        schedule(jobName, poolName, serviceName, dataId, startTime, frequency, interval, count, endTime, maxRetry, (String) null);
    }
}
