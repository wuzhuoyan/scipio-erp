package com.ilscipio.scipio.ce.webapp.ftl.template;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.cache.UtilCache;
import org.ofbiz.base.util.collections.MapStack;
import org.ofbiz.base.util.template.FreeMarkerWorker;

import com.ilscipio.scipio.ce.webapp.ftl.context.ContextFtlUtil;
import com.ilscipio.scipio.ce.webapp.ftl.template.TemplateInvoker.InvokeOptions.InvokeMode;

import freemarker.core.Environment;
import freemarker.ext.util.WrapperTemplateModel;
import freemarker.template.Configuration;
import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateScalarModel;

/**
 * Template invoker base class.
 * Invokes Freemarker template render in a separate render from
 * any current renderer, using options carried in the instance itself,
 * using methods such as Ofbiz's FreeMarkerWorker does, and contrary
 * to the Freemarker <code>?interpret</code> call which inlines
 * in the current renderer (and therefore poses restrictions).
 * <p>
 * This is needed to: 
 * 1) carry the compilation and invocation options around
 * 2) allow nested Freemarker template renders that behave like separate renders 
 *    (and not like inlined interprets like <code>?interpret</code> does)
 * 3) enable evaluating templates using <code>?string</code> operator from freemarker templates
 * 4) allow lazy compilation but that still gets cached
 * <p>
 * For invoking other templates from within an FTL template,
 * StringTemplateInvoker can be used which causes the
 * invocation through the toString() method when this
 * is wrapped in a StringModel by the ObjectWrapper.
 * <p>
 * NOTE: 2017-02: Currently, by default, this relies on BeansWrapper StringModel to call
 * the StringTemplateInvoker.toString() method to do the invocation from templates;
 * this is imperfect but allows consistent unwrapping/rewrapping without needing 
 * to modify the ObjectWrapper. HOWEVER this means we only support the "scalar" model for now.
 * <p>
 * TODO: in future should have a dedicated TemplateDirectiveModel + TemplateScalarModel hybrid + individuals
 * in order to be consistent with the <code>?interpret</code> directive behavior (but needs
 * special code for rewrapping in context, ideally ObjectWrapper.wrap override).
 * <p>
 * TODO?: this currently does not support lazy template compilation... don't see much need.
 */
public class TemplateInvoker {

    public static final String module = TemplateInvoker.class.getName();
    
    protected final TemplateSource templateSource;
    protected final InvokeOptions invokeOptions;
    /**
     * Carries around the preferred FTL model to use when
     * this is wrapped by an ObjectWrapper.
     * CURRENTLY (2017-02), this is always SCALAR.
     * TODO: more usage
     * <p>
     * This is needed here because the FTL unwrapping across macro invocations
     * will cause information loss about the model or more generally form of the wrapper.
     * NOTE: ideally this should be honored by the ObjectWrapper implementation,
     * so for now we are relying on SCALAR and if DIRECTIVE is needed then a
     * manual wrapping call has to be done.
     */
    protected final FtlModel preferredModel;
    
    public static class InvokeOptions {

        public enum InvokeMode {
            /**
             * Standard, standalone Ofbiz-like invocation using FreeMarkerWorker.
             */
            OFBIZ_STD;
            
            /*
             * Inline, simulation of <code>?interpret</code> directive behavior.
             * TODO: not sure this will even possible
             */
            //FTL_INLINE; // TODO
        }
        
        protected final InvokeMode invokeMode;
        
        protected final Map<String, Object> context;
        
        /**
         * If true, pushes the Ofbiz MapStack context before invoke and pops after.
         */
        protected final boolean pushCtx;

        public InvokeOptions(InvokeMode invokeMode, Map<String, Object> context, boolean pushCtx) {
            this.invokeMode = invokeMode;
            this.context = context;
            this.pushCtx = pushCtx;
        }
        
        public InvokeOptions(InvokeMode invokeMode, boolean pushCtx) {
            this.invokeMode = invokeMode;
            this.context = null;
            this.pushCtx = pushCtx;
        }

        public InvokeMode getInvokeMode() {
            return invokeMode;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public boolean isPushCtx() {
            return pushCtx;
        }
    }

    /**
     * FTL TemplateModel type to wrap this TemplateInvoker in.
     * <p>
     * NOTE: <code>SCALAR</code> is unspecific between StringModel-wrapped StringTemplateInvoker
     * and dedicated TemplateScalarModel implementation.
     * <p>
     * Currently (2017-02) our code will be using StringModel-wrapped StringTemplateInvoker.
     */
    public enum FtlModel {
        SCALAR,
        DIRECTIVE,
        HYBRID;
    }
    
    protected TemplateInvoker(TemplateSource templateSource, InvokeOptions invokeOptions, FtlModel preferredModel) {
        this.templateSource = templateSource;
        this.invokeOptions = invokeOptions;
        this.preferredModel = preferredModel;
    }

    /* ***************************************************** */
    /* Factory Methods */
    /* ***************************************************** */
    
    /**
     * Gets a standard template invoker. 
     */
    public static TemplateInvoker getInvoker(TemplateSource templateSource, InvokeOptions invokeOptions, FtlModel preferredModel) throws TemplateException, IOException {
        return new TemplateInvoker(templateSource, invokeOptions, preferredModel);
    }
    
    /**
     * Gets a template invoker same as standard except its toString() method invokes rendering.
     */
    public static TemplateInvoker getStringInvoker(TemplateSource templateSource, InvokeOptions invokeOptions, FtlModel preferredModel) throws TemplateException, IOException {
        return new StringTemplateInvoker(templateSource, invokeOptions, preferredModel);
    }

    
    /* ***************************************************** */
    /* Main Invocation Calls */
    /* ***************************************************** */
    
    /**
     * Invokes rendering and outputs to Writer.
     */
    public void invoke(Writer out) throws TemplateException, IOException {
        Template template = getTemplate();
        Map<String, Object> context = getContext();
        if (invokeOptions.invokeMode == InvokeMode.OFBIZ_STD) {
            if (invokeOptions.isPushCtx()) {
                ((MapStack<String>) context).push();
            }
            try {
                FreeMarkerWorker.renderTemplate(template, context, out);
            } finally {
                ((MapStack<String>) context).pop();
            }
        } else {
            throw new UnsupportedOperationException("Unsupported template invoke mode: " + invokeOptions.invokeMode);
        }
    }
    
    /**
     * Invokes rendering and returns as a String.
     */
    public String invoke() throws TemplateException, IOException {
        StringWriter sw = new StringWriter();
        invoke(sw);
        return sw.toString();
    }
    

    
    /* ***************************************************** */
    /* Getters and Helpers */
    /* ***************************************************** */

    public Template getTemplate() throws TemplateException, IOException {
        return templateSource.getTemplate();
    }
    
    protected TemplateSource getTemplateSource() throws TemplateException, IOException {
        return templateSource;
    }

    public InvokeOptions getInvokeOptions() {
        return invokeOptions;
    }
    
    public FtlModel getPreferredModel() {
        return preferredModel;
    }
    
    protected Map<String, Object> getContext() {
        Map<String, Object> context = invokeOptions.getContext();
        if (context == null) {
            try {
                context = ContextFtlUtil.getContext(FreeMarkerWorker.getCurrentEnvironment());
            } catch (Exception e) {
                Debug.logError(e, module);
            }
        }
        if (context == null) {
            context = MapStack.create();
        }
        return context;
    }

    /* ***************************************************** */
    /* Specific Invoker Implementations */
    /* ***************************************************** */
    
    /**
     * Variant of TemplateInvoker that invokes rendering when toString() is called.
     */
    public static class StringTemplateInvoker extends TemplateInvoker {
        protected StringTemplateInvoker(TemplateSource templateSource, InvokeOptions invokeOptions,
                FtlModel preferredModel) {
            super(templateSource, invokeOptions, preferredModel);
        }

        @Override
        public String toString() {
            try {
                return invoke();
            } catch (TemplateException e) {
                Debug.logError(e, module);
                return "";
            } catch (IOException e) {
                Debug.logError(e, module);
                return "";
            }
        }
    }
    
    /* ***************************************************** */
    /* Invoker Freemarker Wrappers */
    /* ***************************************************** */
    
    /**
     * Wraps the TemplateInvoker in an appropriate TemplateModel, using invoker's preferred model.
     * Can be called manually as this logic may not be present in <code>ObjectWrapper.wrap</code>.
     * <p>
     * TODO: currently (2017-02) this always wraps using StringModel, done by most of the
     * default object wrapper impls.
     */
    @SuppressWarnings("unchecked")
    public static <T extends TemplateModel> T wrap(TemplateInvoker invoker, ObjectWrapper objectWrapper) throws TemplateModelException {
        return wrap(invoker, objectWrapper, invoker.getPreferredModel());
    }
    
    /**
     * Wraps the TemplateInvoker in an appropriate TemplateModel, using explicit model type.
     * Can be called manually as this logic may not be present in <code>ObjectWrapper.wrap</code>.
     */
    @SuppressWarnings("unchecked")
    public static <T extends TemplateModel> T wrap(TemplateInvoker invoker, ObjectWrapper objectWrapper, FtlModel targetModel) throws TemplateModelException {
        if (targetModel == null || targetModel == FtlModel.SCALAR) {
            if (invoker instanceof StringTemplateInvoker) {
                // TODO: REVISIT: in this case, currently relying on StringModel and ignoring ScalarInvokerWrapper.
                // This case currently covers all the practical usage in templates and CMS...
                // NOTE: if this method were called from ObjectWrapper.wrap, would cause endless loop...
                return (T) objectWrapper.wrap(invoker);
            } else {
                return (T) new ScalarInvokerWrapper(invoker);
            }
        } else if (targetModel == FtlModel.DIRECTIVE) {
            return (T) new DirectiveInvokerWrapper(invoker);
        } else if (targetModel == FtlModel.HYBRID) {
            return (T) new HybridInvokerWrapper(invoker);
        }
        throw new UnsupportedOperationException("Unsupported template invoker FTL wrapper model: " + targetModel);
    }
    
    /**
     * Custom Freemarker wrapper TemplateModel around the TemplateInvoker.
     * <p>
     * TODO: currently this is mainly for testing; will be using StringModel-wrapped
     * StringInvokerWrapper in the interim.
     */
    public static abstract class InvokerWrapper implements TemplateModel, WrapperTemplateModel {
        protected final TemplateInvoker invoker;

        protected InvokerWrapper(TemplateInvoker invoker) {
            this.invoker = invoker;
        }
        
        @Override
        public Object getWrappedObject() {
            return invoker;
        }
    }
    
    public static class DirectiveInvokerWrapper extends InvokerWrapper implements TemplateDirectiveModel {
        protected DirectiveInvokerWrapper(TemplateInvoker invoker) {
            super(invoker);
        }

        @Override
        public void execute(Environment env, Map args, TemplateModel[] modelArgs, TemplateDirectiveBody body)
                throws TemplateException, IOException {
            invoker.invoke(env.getOut());
        }
    }
    
    public static class ScalarInvokerWrapper extends InvokerWrapper implements TemplateScalarModel {
        protected ScalarInvokerWrapper(TemplateInvoker invoker) {
            super(invoker);
        }

        @Override
        public String getAsString() throws TemplateModelException {
            try {
                return invoker.invoke();
            } catch (TemplateException e) {
                throw new TemplateModelException(e);
            } catch (IOException e) {
                throw new TemplateModelException(e);
            }
        }
    }

    public static class HybridInvokerWrapper extends InvokerWrapper implements TemplateDirectiveModel, TemplateScalarModel {
        protected HybridInvokerWrapper(TemplateInvoker invoker) {
            super(invoker);
        }

        @Override
        public void execute(Environment env, Map args, TemplateModel[] modelArgs, TemplateDirectiveBody body)
                throws TemplateException, IOException {
            invoker.invoke(env.getOut());
        }

        @Override
        public String getAsString() throws TemplateModelException {
            try {
                return invoker.invoke();
            } catch (TemplateException e) {
                throw new TemplateModelException(e);
            } catch (IOException e) {
                throw new TemplateModelException(e);
            }
        }
    }
    
}
