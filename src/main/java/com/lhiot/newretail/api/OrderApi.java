package com.lhiot.newretail.api;

import com.leon.microx.util.IOUtils;
import com.leon.microx.util.Jackson;
import com.leon.microx.util.StringUtils;
import com.leon.microx.web.result.Tips;
import com.lhiot.newretail.model.NewRetailOrder;
import com.lhiot.newretail.service.NewRetailOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * @author yijun
 */
@Slf4j
@RestController
@Api("订单服务api")
@RequestMapping("/orders")
public class OrderApi {

    private NewRetailOrderService newRetailOrderService;


    @Autowired
    public OrderApi(NewRetailOrderService newRetailOrderService) {

        this.newRetailOrderService = newRetailOrderService;
    }

    @ApiOperation(value = "创建线下新零售订单")
    @ApiImplicitParam(paramType = "body", name = "newRetailOrder", dataType = "NewRetailOrder", required = true, value = "订单传入参数")
    @PostMapping("/found")
    public ResponseEntity<?> createOrder(@RequestBody NewRetailOrder newRetailOrder) {

        if (Objects.isNull(newRetailOrder)) {
            return ResponseEntity.ok(Tips.of(HttpStatus.BAD_REQUEST, "传入参数为空"));
        }
        if (Objects.isNull(newRetailOrder.getOrderProducts()) || newRetailOrder.getOrderProducts().isEmpty()) {
            return ResponseEntity.ok(Tips.of(HttpStatus.BAD_REQUEST, "传入商品参数为空"));
        }
        //处理具体业务
        Tips createResult = newRetailOrderService.createOrder(newRetailOrder);
        return ResponseEntity.ok(createResult);
    }

    @ApiOperation(value = "线上下单成功(如美团外卖)->发送海鼎减库存->推送订单到新零售门店")
    @ApiImplicitParam(paramType = "path", name = "orderCode", dataType = "String", value = "业务订单编码")
    @PostMapping("{orderCode}/to-store")
    public ResponseEntity<?> pushOrderToStore(@PathVariable("orderCode") String orderCode) {

        //查询业务订单
        NewRetailOrder newRetailOrder = newRetailOrderService.getOrderByOrderCode(orderCode);
        if(Objects.isNull(newRetailOrder))
            return ResponseEntity.badRequest().body(Tips.of(HttpStatus.BAD_REQUEST,"未找到订单"));
        //通过 restTemplate 推送订单
        Tips pushOrderToStore = newRetailOrderService.pushOrderToStore(newRetailOrder);
        if (pushOrderToStore.err()) {
            ResponseEntity.badRequest().body(pushOrderToStore);
        }
        return ResponseEntity.ok(pushOrderToStore);
    }

/*    @ApiOperation(value = "批量取消")
    @PostMapping("batch-cancel")
    public ResponseEntity<?> batchCancel(@RequestParam("reason") String reason,@RequestBody String[] orderCodes){
        String result =newRetailOrderService.batchCancel(orderCodes,reason);
        if(StringUtils.isBlank(result)){
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok().build();
    }
*/
    @ApiOperation(value = "批量推送")
    @PostMapping("batch-push")
    public ResponseEntity<?> batchPush(@RequestBody String[] orderCodes){
        newRetailOrderService.batchPush(orderCodes);
        return ResponseEntity.ok().build();
    }



    @ApiOperation(value = "货物从门店出门")
    @ApiImplicitParam(paramType = "path", name = "orderCode", dataType = "String", value = "业务订单id")
    @PutMapping("{orderCode}/finsh")
    public ResponseEntity<?> orderOutOfStore(@PathVariable("orderCode") String orderCode) {
        Tips outOfStoreResult = newRetailOrderService.orderOutOfStore(orderCode);
        return outOfStoreResult.err() ? ResponseEntity.ok(Tips.of(-1, outOfStoreResult.getMessage())) :
                ResponseEntity.ok(Tips.of(1, String.format("{\"hdOrderCode\":%d}", orderCode)));
    }


    @PostMapping("/hai-ding/callback")
    @ApiOperation(value = "海鼎回调处理订单")
    public ResponseEntity haidingCallback(HttpServletRequest request) {

        Map<String, Object> parameters = this.convertRequestParameters(request);
        Tips hdCallbackDeal = newRetailOrderService.hdCallbackDeal(parameters);
        if (hdCallbackDeal.err()) {
            return ResponseEntity.badRequest().body(hdCallbackDeal);
        }
        return ResponseEntity.ok(hdCallbackDeal);
    }

    @PostMapping("/hai-ding/callback/old")
    @ApiOperation(value = "海鼎回调处理订单-通过原来的处理过来的数据")
    public ResponseEntity haidingCallback(@RequestBody String hdResult) {
        log.info("haidingCallback:",hdResult);
        if(hdResult.indexOf("store.skuchanged")!=-1){
            return ResponseEntity.ok().build();
        }
        Map<String, Object> parameters = Jackson.map(hdResult);
        Tips hdCallbackDeal = newRetailOrderService.hdCallbackDeal(parameters);
        if (hdCallbackDeal.err()) {
            log.info("haidingCallback处理错误:{}",hdCallbackDeal);
        }
        return ResponseEntity.ok(hdCallbackDeal);
    }


    @PostMapping("/deliver/dada/callback")
    @ApiOperation(value = "达达配送回调处理订单")
    public ResponseEntity deliverCallback(HttpServletRequest request) {

        Map<String, Object> parameters = this.convertRequestParameters(request);
        Tips deliverCallbackDeal = newRetailOrderService.deliverCallbackDeal(parameters);
        if (deliverCallbackDeal.err()) {
            return ResponseEntity.badRequest().body(deliverCallbackDeal);
        }
        return ResponseEntity.ok(deliverCallbackDeal);
    }

    @Nullable
    private Map<String, Object> convertRequestParameters(HttpServletRequest request) {
        Map<String, Object> parameters = null;
        try (InputStream inputStream = request.getInputStream()) {
            if (Objects.nonNull(inputStream)) {
                @Cleanup BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String parameterString = StringUtils.collectionToDelimitedString(IOUtils.readLines(in),"");
                String resultStr=parameterString.replaceAll("\"\\{","{").replaceAll("}\"","}").replaceAll("\\\\","");
                log.info("request转换成字符串结果：{}", resultStr);
                if (StringUtils.isNotBlank(resultStr)) {
                    parameters = Jackson.map(resultStr);
                }
            }
        } catch (IOException ignore) {
            log.error("convertRequestParameters",ignore);
        }
        return parameters;
    }

}
