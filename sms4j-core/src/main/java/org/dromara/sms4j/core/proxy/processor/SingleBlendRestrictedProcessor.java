package org.dromara.sms4j.core.proxy.processor;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.dao.SmsDao;
import org.dromara.sms4j.api.proxy.SmsProcessor;
import org.dromara.sms4j.api.proxy.aware.SmsBlendConfigAware;
import org.dromara.sms4j.api.proxy.aware.SmsDaoAware;
import org.dromara.sms4j.comm.exception.SmsBlendException;
import org.dromara.sms4j.comm.utils.SmsUtils;

import java.lang.reflect.Method;
import java.util.Map;


/**
 * 短信发送渠道级上限前置拦截执行器
 *
 * @author sh1yu
 * @since 2023/10/27 13:03
 */
@Setter
@Slf4j
public class SingleBlendRestrictedProcessor implements SmsProcessor, SmsDaoAware, SmsBlendConfigAware {

    private static final String REDIS_KEY = "sms:restricted:";

    /**
     * 缓存实例
     */
    private SmsDao smsDao;

    Map smsBlendsConfig;

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public Object[] preProcessor(Method method, Object source, Object[] param) {
        String name = method.getName();
        if (!"sendMessage".equals(name) && !"massText".equals(name)) {
            return param;
        }
        SmsBlend smsBlend = (SmsBlend) source;
        String configId = smsBlend.getConfigId();
        Map targetConfig = (Map) smsBlendsConfig.get(configId);
        Object maximumObj = targetConfig.get("maximum");
        if (SmsUtils.isEmpty(maximumObj)) {
            return param;
        }
        int maximum = 0;
        try{
             maximum = Integer.parseInt(String.valueOf(maximumObj)) ;
        }catch (Exception e){
            log.error("获取厂商级发送上限参数错误！请检查！");
            throw new IllegalArgumentException("获取厂商级发送上限参数错误");
        }
        Integer i = (Integer) smsDao.get(REDIS_KEY + configId + "maximum");
        if (SmsUtils.isEmpty(i)) {
            smsDao.set(REDIS_KEY + configId + "maximum", 1);
        } else if (i >= maximum) {
            log.info("The channel: {},messages reached the maximum", configId);
            throw new SmsBlendException("The channel: {},messages reached the maximum", configId);
        } else {
            smsDao.set(REDIS_KEY + configId + "maximum", i + 1);
        }
        return param;
    }
}
