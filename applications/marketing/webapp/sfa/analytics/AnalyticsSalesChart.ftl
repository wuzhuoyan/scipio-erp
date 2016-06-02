<#assign chartType=chartType!"bar"/>    <#-- (line|bar|pie) default: line -->
<#assign library=chartLibrary!"chart"/>
<#assign datasets=chartDatasets?number!1 />

<#if sales?has_content> 
    <#if chartType == "line" || chartType == "bar">        
        <@chart type=chartType library=library xlabel=xlabel!"" ylabel=ylabel!"" label1=label1!"" label2=label2!"">
            <#list sales.keySet() as key>
                <#assign currData = sales[key] />
                ${Static["org.ofbiz.base.util.Debug"].log("currData =====> " + currData)}
                <#if currData?has_content>                          
                   <@chartdata value="${(currData.total)!0}" value2="${(currData.count)!0}" title="${key}"/>
                </#if>
            </#list>
        </@chart>
    <#elseif chartType == "pie">
        <@commonMsg type="error">${uiLabelMap.CommonUnsupported}</@commonMsg>
    <#else>
        <@commonMsg type="error">${uiLabelMap.CommonUnsupported}</@commonMsg>
    </#if>
<#else>
    <@commonMsg type="result-norecord"/>            
</#if>