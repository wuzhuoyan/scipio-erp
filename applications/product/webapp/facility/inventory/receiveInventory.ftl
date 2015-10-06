<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
<script language="JavaScript" type="text/javascript">
    function setNow(field) { eval('document.selectAllForm.' + field + '.value="${nowTimestamp}"'); }
</script>

<#assign menuHtml>
  <@menu type="section" inlineItems=true>
    <@menuitem type="link" href=makeOfbizUrl("EditFacility") text="${uiLabelMap.ProductNewFacility}" contentClass="+create" />
  </@menu>
</#assign>
<@section menuHtml=menuHtml>
        <#if invalidProductId??>
            <@alert type="error">${invalidProductId}</@alert>
        </#if>

        <#-- Receiving Results -->
        <#if receivedItems?has_content>
          <@section title="${uiLabelMap.ProductReceiptPurchaseOrder} ${purchaseOrder.orderId}">
          <@table type="data-list" class="basic-table" cellspacing="0">
           <@thead>
            <@tr class="header-row">
              <@th>${uiLabelMap.ProductShipmentId}</@th>
              <@th>${uiLabelMap.ProductReceipt}</@th>
              <@th>${uiLabelMap.CommonDate}</@th>
              <@th>${uiLabelMap.ProductPo}</@th>
              <@th>${uiLabelMap.ProductLine}</@th>
              <@th>${uiLabelMap.ProductProductId}</@th>
              <@th>${uiLabelMap.ProductLotId}</@th>
              <@th>${uiLabelMap.ProductPerUnitPrice}</@th>
              <@th>${uiLabelMap.CommonRejected}</@th>
              <@th>${uiLabelMap.CommonAccepted}</@th>
              <@th></@th>
            </@tr>
            </@thead>
            <#list receivedItems as item>
              <form name="cancelReceivedItemsForm_${item_index}" method="post" action="<@ofbizUrl>cancelReceivedItems</@ofbizUrl>">
                <input type="hidden" name="receiptId" value ="${(item.receiptId)!}"/>
                <input type="hidden" name="purchaseOrderId" value ="${(item.orderId)!}"/>
                <input type="hidden" name="facilityId" value ="${facilityId!}"/>
                <@tr>
                  <@td><a href="<@ofbizUrl>ViewShipment?shipmentId=${item.shipmentId!}</@ofbizUrl>" class="${styles.button_default!}">${item.shipmentId!} ${item.shipmentItemSeqId!}</a></@td>
                  <@td>${item.receiptId}</@td>
                  <@td>${item.getString("datetimeReceived").toString()}</@td>
                  <@td><a href="/ordermgr/control/orderview?orderId=${item.orderId}" class="${styles.button_default!}">${item.orderId}</a></@td>
                  <@td>${item.orderItemSeqId}</@td>
                  <@td>${item.productId?default("Not Found")}</@td>
                  <@td>${item.lotId?default("")}</@td>
                  <@td>${item.unitCost?default(0)?string("##0.00")}</@td>
                  <@td>${item.quantityRejected?default(0)?string.number}</@td>
                  <@td>${item.quantityAccepted?string.number}</@td>
                  <@td>
                    <#if (item.quantityAccepted?int > 0 || item.quantityRejected?int > 0)>
                      <a href="javascript:document.cancelReceivedItemsForm_${item_index}.submit();" class="${styles.button_default!}">${uiLabelMap.CommonCancel}</a>
                    </#if>
                  </@td>
                </@tr>
              </form>
            </#list>
            <@tr type="util"><@td colspan="10"><hr /></@td></@tr>
          </@table>
          </@section>
        </#if>

        <#-- Single Product Receiving -->
        <#if requestParameters.initialSelected?? && product?has_content>
          <@section>
          <form method="post" action="<@ofbizUrl>receiveSingleInventoryProduct</@ofbizUrl>" name="selectAllForm">
              <#-- general request fields -->
              <input type="hidden" name="facilityId" value="${requestParameters.facilityId!}"/>
              <input type="hidden" name="purchaseOrderId" value="${requestParameters.purchaseOrderId!}"/>
              <#-- special service fields -->
              <input type="hidden" name="productId" value="${requestParameters.productId!}"/>
              <#if purchaseOrder?has_content>
              <#assign unitCost = firstOrderItem.unitPrice?default(standardCosts.get(firstOrderItem.productId)?default(0))/>
              <input type="hidden" name="orderId" value="${purchaseOrder.orderId}"/>
              <input type="hidden" name="orderItemSeqId" value="${firstOrderItem.orderItemSeqId}"/>
              <@field type="generic" label="${uiLabelMap.ProductPurchaseOrder}">
                  <b>${purchaseOrder.orderId}</b>&nbsp;/&nbsp;<b>${firstOrderItem.orderItemSeqId}</b>
                  <#if 1 < purchaseOrderItems.size()>
                    (${uiLabelMap.ProductMultipleOrderItemsProduct} - ${purchaseOrderItems.size()}:1 ${uiLabelMap.ProductItemProduct})
                  <#else>
                    (${uiLabelMap.ProductSingleOrderItemProduct} - 1:1 ${uiLabelMap.ProductItemProduct})
                  </#if>
              </@field>
              </#if>
              <@field type="generic" label="${uiLabelMap.ProductProductId}">
                  <b>${requestParameters.productId!}</b>
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductProductName}">
                  <a href="/catalog/control/EditProduct?productId=${product.productId}${externalKeyParam!}" target="catalog" class="${styles.button_default!}">${product.internalName!}</a>
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductProductDescription}">
                  ${product.description!}
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductItemDescription}">
                  <input type="text" name="itemDescription" size="30" maxlength="60"/>
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductInventoryItemType}">
                  <select name="inventoryItemTypeId" size="1">
                    <#list inventoryItemTypes as nextInventoryItemType>
                      <option value="${nextInventoryItemType.inventoryItemTypeId}"
                        <#if (facility.defaultInventoryItemTypeId?has_content) && (nextInventoryItemType.inventoryItemTypeId == facility.defaultInventoryItemTypeId)>
                          selected="selected"
                        </#if>
                      >${nextInventoryItemType.get("description",locale)?default(nextInventoryItemType.inventoryItemTypeId)}</option>
                    </#list>
                  </select>
              </@field>

              <hr />
              
              <@field type="generic" label="${uiLabelMap.ProductFacilityOwner}">
                  <@htmlTemplate.lookupField formName="selectAllForm" name="ownerPartyId" id="ownerPartyId" fieldFormName="LookupPartyName"/>
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductSupplier}">
                  <select name="partyId">
                    <option value=""></option>
                    <#if supplierPartyIds?has_content>
                      <#list supplierPartyIds as supplierPartyId>
                        <option value="${supplierPartyId}" <#if supplierPartyId == parameters.partyId!> selected="selected"</#if>>
                          [${supplierPartyId}] ${Static["org.ofbiz.party.party.PartyHelper"].getPartyName(delegator, supplierPartyId, true)}
                        </option>
                      </#list>
                    </#if>
                  </select>
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductDateReceived}">
                  <input type="text" name="datetimeReceived" size="24" value="${nowTimestamp}" />
                  <#-- <a href="#" onclick="setNow("datetimeReceived")" class="${styles.button_default!}">[Now]</a> -->
              </@field>
              
              
              <@field type="generic" label="${uiLabelMap.lotId}">
                  <input type="text" name="lotId" size="10"/>
              </@field>

              <#-- facility location(s) -->
              <#assign facilityLocations = (product.getRelated("ProductFacilityLocation", Static["org.ofbiz.base.util.UtilMisc"].toMap("facilityId", facilityId), null, false))!/>
              <@field type="generic" label="${uiLabelMap.ProductFacilityLocation}">
                  <#if facilityLocations?has_content>
                    <select name="locationSeqId">
                      <#list facilityLocations as productFacilityLocation>
                        <#assign facility = productFacilityLocation.getRelatedOne("Facility", true)/>
                        <#assign facilityLocation = productFacilityLocation.getRelatedOne("FacilityLocation", false)!/>
                        <#assign facilityLocationTypeEnum = (facilityLocation.getRelatedOne("TypeEnumeration", true))!/>
                        <option value="${productFacilityLocation.locationSeqId}"><#if facilityLocation??>${facilityLocation.areaId!}:${facilityLocation.aisleId!}:${facilityLocation.sectionId!}:${facilityLocation.levelId!}:${facilityLocation.positionId!}</#if><#if facilityLocationTypeEnum??>(${facilityLocationTypeEnum.get("description",locale)})</#if>[${productFacilityLocation.locationSeqId}]</option>
                      </#list>
                      <option value="">${uiLabelMap.ProductNoLocation}</option>
                    </select>
                  <#else>
                    <#if parameters.facilityId??>
                      <#assign LookupFacilityLocationView="LookupFacilityLocation?facilityId=${facilityId}">
                    <#else>
                      <#assign LookupFacilityLocationView="LookupFacilityLocation">
                    </#if>
                    <@htmlTemplate.lookupField formName="selectAllForm" name="locationSeqId" id="locationSeqId" fieldFormName="${LookupFacilityLocationView}"/>
                  </#if>
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductRejectedReason}">
                  <select name="rejectionId" size="1">
                    <option></option>
                    <#list rejectReasons as nextRejection>
                      <option value="${nextRejection.rejectionId}">${nextRejection.get("description",locale)?default(nextRejection.rejectionId)}</option>
                    </#list>
                  </select>
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductQuantityRejected}">
                  <input type="text" name="quantityRejected" size="5" value="0" />
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductQuantityAccepted}">
                  <input type="text" name="quantityAccepted" size="5" value="${defaultQuantity?default(1)?string.number}"/>
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductPerUnitPrice}">
                  <#-- get the default unit cost -->
                  <#if (!unitCost?? || unitCost == 0.0)><#assign unitCost = standardCosts.get(product.productId)?default(0)/></#if>
                  <input type="text" name="unitCost" size="10" value="${unitCost}"/>
              </@field>
              <@field type="submitarea">
                  <input type="submit" value="${uiLabelMap.CommonReceive}" />
              </@field>
            <script language="JavaScript" type="text/javascript">
              document.selectAllForm.quantityAccepted.focus();
            </script>
          </form>
          </@section>
        <#-- Select Shipment Screen -->
        <#elseif requestParameters.initialSelected?? && !requestParameters.shipmentId??>
          <@section title="${uiLabelMap.ProductSelectShipmentReceive}">
          <form method="post" action="<@ofbizUrl>ReceiveInventory</@ofbizUrl>" name="selectAllForm">
            <#-- general request fields -->
            <input type="hidden" name="facilityId" value="${requestParameters.facilityId!}"/>
            <input type="hidden" name="purchaseOrderId" value="${requestParameters.purchaseOrderId!}"/>
            <input type="hidden" name="initialSelected" value="Y"/>
            <input type="hidden" name="partialReceive" value="${partialReceive!}"/>
            <@table type="generic" class="basic-table" cellspacing="0">
              <#list shipments! as shipment>
                <#assign originFacility = shipment.getRelatedOne("OriginFacility", true)!/>
                <#assign destinationFacility = shipment.getRelatedOne("DestinationFacility", true)!/>
                <#assign statusItem = shipment.getRelatedOne("StatusItem", true)/>
                <#assign shipmentType = shipment.getRelatedOne("ShipmentType", true)/>
                <#assign shipmentDate = shipment.estimatedArrivalDate!/>
                <@tr type="util">
                  <@td><hr /></@td>
                </@tr>
                <@tr>
                  <@td>
                    <@table type="fields" class="basic-table" cellspacing="0">
                      <@tr>
                        <@td width="5%" nowrap="nowrap"><input type="radio" name="shipmentId" value="${shipment.shipmentId}" /></@td>
                        <@td width="5%" nowrap="nowrap">${shipment.shipmentId}</@td>
                        <@td>${shipmentType.get("description",locale)?default(shipmentType.shipmentTypeId?default(""))}</@td>
                        <@td>${statusItem.get("description",locale)?default(statusItem.statusId?default("N/A"))}</@td>
                        <@td>${(originFacility.facilityName)!} [${shipment.originFacilityId!}]</@td>
                        <@td>${(destinationFacility.facilityName)!} [${shipment.destinationFacilityId!}]</@td>
                        <@td style="white-space: nowrap;">${(shipment.estimatedArrivalDate.toString())!}</@td>
                      </@tr>
                    </@table>
                  </@td>
                </@tr>
              </#list>
              <@tr type="util">
                <@td><hr /></@td>
              </@tr>
              <@tr>
                <@td>
                  <@table type="fields" class="basic-table" cellspacing="0">
                    <@tr>
                      <@td width="5%" nowrap="nowrap"><input type="radio" name="shipmentId" value="_NA_" /></@td>
                      <@td width="5%" nowrap="nowrap">${uiLabelMap.ProductNoSpecificShipment}</@td>
                      <@td colspan="5"></@td>
                    </@tr>
                  </@table>
                </@td>
              </@tr>
              <@tr>
                <@td>&nbsp;<a href="javascript:document.selectAllForm.submit();" class="${styles.button_default!}">${uiLabelMap.ProductReceiveSelectedShipment}</a></@td>
              </@tr>
            </@table>
          </form>
          </@section>
        <#-- Multi-Item PO Receiving -->
        <#elseif requestParameters.initialSelected?? && purchaseOrder?has_content>
          <@section>
          <input type="hidden" id="getConvertedPrice" value="<@ofbizUrl secure="${request.isSecure()?string}">getConvertedPrice"</@ofbizUrl> />
          <input type="hidden" id="alertMessage" value="${uiLabelMap.ProductChangePerUnitPrice}" />
          <form method="post" action="<@ofbizUrl>receiveInventoryProduct</@ofbizUrl>" name="selectAllForm">
            <#-- general request fields -->
            <input type="hidden" name="facilityId" value="${requestParameters.facilityId!}"/>
            <input type="hidden" name="purchaseOrderId" value="${requestParameters.purchaseOrderId!}"/>
            <input type="hidden" name="initialSelected" value="Y"/>
            <#if shipment?has_content>
            <input type="hidden" name="shipmentIdReceived" value="${shipment.shipmentId}"/>
            </#if>
            <input type="hidden" name="_useRowSubmit" value="Y"/>
            <#assign rowCount = 0/>
          <#if !purchaseOrderItems?? || purchaseOrderItems.size() == 0>
            <@resultMsg>${uiLabelMap.ProductNoItemsPoReceive}.</@resultMsg>
          <#else>
            <@table type="fields" class="basic-table" cellspacing="0">
                <@tr>
                  <@td>
                    <@heading>${uiLabelMap.ProductReceivePurchaseOrder} #${purchaseOrder.orderId}</@heading>
                    <#if shipment?has_content>
                    <@heading>${uiLabelMap.ProductShipmentId} #${shipment.shipmentId}</@heading>
                    <span>Set Shipment As Received</span>&nbsp;
                    <input type="checkbox" name="forceShipmentReceived" value="Y"/>
                    </#if>
                  </@td>
                  <@td align="right">
                    ${uiLabelMap.CommonSelectAll}
                    <input type="checkbox" name="selectAll" value="Y" onclick="javascript:toggleAll(this, 'selectAllForm');"/>
                  </@td>
                </@tr>
                <#list purchaseOrderItems as orderItem>
                  <#assign defaultQuantity = orderItem.quantity - receivedQuantities[orderItem.orderItemSeqId]?double/>
                  <#assign itemCost = orderItem.unitPrice?default(0)/>
                  <#assign salesOrderItem = salesOrderItems[orderItem.orderItemSeqId]!/>
                  <#if shipment?has_content>
                    <#if shippedQuantities[orderItem.orderItemSeqId]??>
                      <#assign defaultQuantity = shippedQuantities[orderItem.orderItemSeqId]?double - receivedQuantities[orderItem.orderItemSeqId]?double/>
                    <#else>
                      <#assign defaultQuantity = 0/>
                    </#if>
                  </#if>
                  <#if 0 < defaultQuantity>
                  <#assign orderItemType = orderItem.getRelatedOne("OrderItemType", false)/>
                  <input type="hidden" name="orderId_o_${rowCount}" value="${orderItem.orderId}"/>
                  <input type="hidden" name="orderItemSeqId_o_${rowCount}" value="${orderItem.orderItemSeqId}"/>
                  <input type="hidden" name="facilityId_o_${rowCount}" value="${requestParameters.facilityId!}"/>
                  <input type="hidden" name="datetimeReceived_o_${rowCount}" value="${nowTimestamp}"/>
                  <#if shipment?? && shipment.shipmentId?has_content>
                    <input type="hidden" name="shipmentId_o_${rowCount}" value="${shipment.shipmentId}"/>
                  </#if>
                  <#if salesOrderItem?has_content>
                    <input type="hidden" name="priorityOrderId_o_${rowCount}" value="${salesOrderItem.orderId}"/>
                    <input type="hidden" name="priorityOrderItemSeqId_o_${rowCount}" value="${salesOrderItem.orderItemSeqId}"/>
                  </#if>

                  <@tr type="util">
                    <@td colspan="2"><hr /></@td>
                  </@tr>
                  <@tr>
                    <@td>
                      <@table type="fields" class="basic-table" cellspacing="0">
                        <@tr>
                          <#if orderItem.productId??>
                            <#assign product = orderItem.getRelatedOne("Product", true)/>
                            <input type="hidden" name="productId_o_${rowCount}" value="${product.productId}"/>
                            <@td width="45%">
                                ${orderItem.orderItemSeqId}:&nbsp;<a href="/catalog/control/EditProduct?productId=${product.productId}${externalKeyParam!}" target="catalog" class="${styles.button_default!}">${product.productId}&nbsp;-&nbsp;${orderItem.itemDescription!}</a> : ${product.description!}
                            </@td>
                          <#else>
                            <@td width="45%">
                                <b>${orderItemType.get("description",locale)}</b> : ${orderItem.itemDescription!}&nbsp;&nbsp;
                                <input type="text" size="12" name="productId_o_${rowCount}"/>
                                <a href="/catalog/control/EditProduct?${StringUtil.wrapString(externalKeyParam)}" target="catalog" class="${styles.button_default!}">${uiLabelMap.ProductCreateProduct}</a>
                            </@td>
                          </#if>
                          <@td align="right">${uiLabelMap.ProductLocation}:</@td>
                          <#-- location(s) -->
                          <@td align="right">
                            <#assign facilityLocations = (orderItem.getRelated("ProductFacilityLocation", Static["org.ofbiz.base.util.UtilMisc"].toMap("facilityId", facilityId), null, false))!/>
                            <#if facilityLocations?has_content>
                              <select name="locationSeqId_o_${rowCount}">
                                <#list facilityLocations as productFacilityLocation>
                                  <#assign facility = productFacilityLocation.getRelatedOne("Facility", true)/>
                                  <#assign facilityLocation = productFacilityLocation.getRelatedOne("FacilityLocation", false)!/>
                                  <#assign facilityLocationTypeEnum = (facilityLocation.getRelatedOne("TypeEnumeration", true))!/>
                                  <option value="${productFacilityLocation.locationSeqId}"><#if facilityLocation??>${facilityLocation.areaId!}:${facilityLocation.aisleId!}:${facilityLocation.sectionId!}:${facilityLocation.levelId!}:${facilityLocation.positionId!}</#if><#if facilityLocationTypeEnum??>(${facilityLocationTypeEnum.get("description",locale)})</#if>[${productFacilityLocation.locationSeqId}]</option>
                                </#list>
                                <option value="">${uiLabelMap.ProductNoLocation}</option>
                              </select>
                            <#else>
                              <#if parameters.facilityId??>
                                <#assign LookupFacilityLocationView="LookupFacilityLocation?facilityId=${facilityId}">
                              <#else>
                                <#assign LookupFacilityLocationView="LookupFacilityLocation">
                              </#if>
                              <@htmlTemplate.lookupField formName="selectAllForm" name="locationSeqId_o_${rowCount}" id="locationSeqId_o_${rowCount}" fieldFormName="${LookupFacilityLocationView}"/>
                            </#if>
                          </@td>
                          <@td align="right">${uiLabelMap.ProductQtyReceived} :</@td>
                          <@td align="right">
                            <input type="text" name="quantityAccepted_o_${rowCount}" size="6" value=<#if partialReceive??>"0"<#else>"${defaultQuantity?string.number}"</#if>/>
                          </@td>
                        </@tr>
                        <@tr>
                          <@td width="45%">
                            ${uiLabelMap.ProductInventoryItemType} :&nbsp;
                            <select name="inventoryItemTypeId_o_${rowCount}" size="1">
                              <#list inventoryItemTypes as nextInventoryItemType>
                              <option value="${nextInventoryItemType.inventoryItemTypeId}"
                               <#if (facility.defaultInventoryItemTypeId?has_content) && (nextInventoryItemType.inventoryItemTypeId == facility.defaultInventoryItemTypeId)>
                                selected="selected"
                              </#if>
                              >${nextInventoryItemType.get("description",locale)?default(nextInventoryItemType.inventoryItemTypeId)}</option>
                              </#list>
                            </select>
                          </@td>
                          <@td align="right">${uiLabelMap.ProductRejectionReason} :</@td>
                          <@td align="right">
                            <select name="rejectionId_o_${rowCount}" size="1">
                              <option></option>
                              <#list rejectReasons as nextRejection>
                              <option value="${nextRejection.rejectionId}">${nextRejection.get("description",locale)?default(nextRejection.rejectionId)}</option>
                              </#list>
                            </select>
                          </@td>
                          <@td align="right">${uiLabelMap.ProductQtyRejected} :</@td>
                          <@td align="right">
                            <input type="text" name="quantityRejected_o_${rowCount}" value="0" size="6"/>
                          </@td>
                          <@tr>
                            <@td>&nbsp;</@td>
                            <#if !product.lotIdFilledIn?has_content || product.lotIdFilledIn != "Forbidden">
                              <@td align="right">${uiLabelMap.ProductLotId}</@td>
                              <@td align="right">
                                <input type="text" name="lotId_o_${rowCount}" size="20" />
                              </@td>
                            <#else>
                              <@td align="right">&nbsp;</@td>
                              <@td align="right">&nbsp;</@td>
                            </#if>
                            <@td align="right">${uiLabelMap.OrderQtyOrdered} :</@td>
                            <@td align="right">
                              <input type="text" class="inputBox" name="quantityOrdered" value="${orderItem.quantity}" size="6" maxlength="20" disabled="disabled" />
                            </@td>
                          </@tr>
                        </@tr>
                        <@tr>
                          <@td>&nbsp;</@td>
                          <@td align="right">${uiLabelMap.ProductFacilityOwner}:</@td>
                          <@td align="right"><input type="text" name="ownerPartyId_o_${rowCount}" size="20" maxlength="20" value="${facility.ownerPartyId}"/></@td>
                          <#if currencyUomId?default('') != orderCurrencyUomId?default('')>
                            <@td>${uiLabelMap.ProductPerUnitPriceOrder}:</@td>
                            <@td>
                              <input type="hidden" name="orderCurrencyUomId_o_${rowCount}" value="${orderCurrencyUomId!}" />
                              <input type="text" id="orderCurrencyUnitPrice_${rowCount}" name="orderCurrencyUnitPrice_o_${rowCount}" value="${orderCurrencyUnitPriceMap[orderItem.orderItemSeqId]}" onchange="javascript:getConvertedPrice(orderCurrencyUnitPrice_${rowCount}, '${orderCurrencyUomId}', '${currencyUomId}', '${rowCount}', '${orderCurrencyUnitPriceMap[orderItem.orderItemSeqId]}', '${itemCost}');" size="6" maxlength="20" />
                              ${orderCurrencyUomId!}
                            </@td>
                            <@td>${uiLabelMap.ProductPerUnitPriceFacility}:</@td>
                            <@td>
                              <input type="hidden" name="currencyUomId_o_${rowCount}" value="${currencyUomId!}" />
                              <input type="text" id="unitCost_${rowCount}" name="unitCost_o_${rowCount}" value="${itemCost}" readonly="readonly" size="6" maxlength="20" />
                              ${currencyUomId!}
                            </@td>
                          <#else>
                            <@td align="right">${uiLabelMap.ProductPerUnitPrice}:</@td>
                            <@td align="right">
                              <input type="hidden" name="currencyUomId_o_${rowCount}" value="${currencyUomId!}" />
                              <input type="text" name="unitCost_o_${rowCount}" value="${itemCost}" size="6" maxlength="20" />
                              ${currencyUomId!}
                            </@td>
                          </#if>
                        </@tr>
                      </@table>
                    </@td>
                    <@td align="right">
                      <input type="checkbox" name="_rowSubmit_o_${rowCount}" value="Y" onclick="javascript:checkToggle(this, 'selectAllForm');"/>
                    </@td>
                  </@tr>
                  <#assign rowCount = rowCount + 1>
                  </#if>
                </#list>
                <@tr type="util">
                  <@td colspan="2">
                    <hr />
                  </@td>
                </@tr>
                <#if rowCount == 0>
                  <@tr>
                    <@td colspan="2">${uiLabelMap.ProductNoItemsPo} #${purchaseOrder.orderId} ${uiLabelMap.ProductToReceive}.</@td>
                  </@tr>
                  <@tr>
                    <@td colspan="2" align="right">
                      <a href="<@ofbizUrl>ReceiveInventory?facilityId=${requestParameters.facilityId!}</@ofbizUrl>" class="${styles.button_default!}">${uiLabelMap.ProductReturnToReceiving}</a>
                    </@td>
                  </@tr>
                <#else>
                  <@tr>
                    <@td colspan="2" align="right">
                      <a href="javascript:document.selectAllForm.submit();" class="${styles.button_default!}">${uiLabelMap.ProductReceiveSelectedProduct}</a>
                    </@td>
                  </@tr>
                </#if>
            </@table>
          </#if>
            <input type="hidden" name="_rowCount" value="${rowCount}"/>
          </form>
          <script language="JavaScript" type="text/javascript">selectAll('selectAllForm');</script>
          </@section>
        <#-- Initial Screen -->
        <#else>
          <@section title="${uiLabelMap.ProductReceiveItem}">
          <form name="selectAllForm" method="post" action="<@ofbizUrl>ReceiveInventory</@ofbizUrl>">
            <input type="hidden" name="facilityId" value="${requestParameters.facilityId!}"/>
            <input type="hidden" name="initialSelected" value="Y"/>
              <@field type="generic" label="${uiLabelMap.ProductPurchaseOrderNumber}" tooltip="${uiLabelMap.ProductLeaveSingleProductReceiving}">
                  <@htmlTemplate.lookupField value="${requestParameters.purchaseOrderId!}" formName="selectAllForm" name="purchaseOrderId" id="purchaseOrderId" fieldFormName="LookupPurchaseOrderHeaderAndShipInfo"/>
              </@field>
              <@field type="generic" label="${uiLabelMap.ProductProductId}" tooltip="${uiLabelMap.ProductLeaveEntirePoReceiving}">
                  <@htmlTemplate.lookupField value="${requestParameters.productId!}" formName="selectAllForm" name="productId" id="productId" fieldFormName="LookupProduct"/>
              </@field>
              <@field type="submitarea">
                  <a href="javascript:document.selectAllForm.submit();" class="${styles.button_default!}">${uiLabelMap.ProductReceiveProduct}</a>
              </@field>
          </form>
          </@section>
        </#if>
        
</@section>