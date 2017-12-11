package support;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.ArrayList;
import java.util.List;

@Log
@RequiredArgsConstructor
public class AdviceInjector {

    private final ApplicationContext applicationContext;

    public List<Object> getControllerAdvices() {
        if (applicationContext == null) {
            return null;
        }

        log.info("Looking for @ControllerAdvice: " + applicationContext);

        List<ControllerAdviceBean> beans = ControllerAdviceBean.findAnnotatedBeans(applicationContext);
        AnnotationAwareOrderComparator.sort(beans);

        List<Object> requestResponseBodyAdviceBeans = new ArrayList<>();

        for (ControllerAdviceBean bean : beans) {
            if (RequestBodyAdvice.class.isAssignableFrom(bean.getBeanType())) {
                requestResponseBodyAdviceBeans.add(bean);
                log.info("Detected RequestBodyAdvice bean in " + bean);
            }
            if (ResponseBodyAdvice.class.isAssignableFrom(bean.getBeanType())) {
                requestResponseBodyAdviceBeans.add(bean);
                log.info("Detected ResponseBodyAdvice bean in " + bean);

            }
        }

        return requestResponseBodyAdviceBeans;
    }

}
