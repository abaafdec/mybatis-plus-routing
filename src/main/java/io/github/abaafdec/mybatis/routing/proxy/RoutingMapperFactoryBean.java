package io.github.abaafdec.mybatis.routing.proxy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Proxy;
import java.util.Map;

public class RoutingMapperFactoryBean<T> implements FactoryBean<T>, BeanFactoryAware {

    private Class<T> mapperInterface;

    private String originMapperBeanName;

    private String dispatchTo;

    private BeanFactory beanFactory;

    private Map<String, String> dispatchOriginMapperMappings;

    private Map<String, String> dispatchImplMapperMappings;


    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    public void setMapperInterface(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    public void setOriginMapperBeanName(String originMapperBeanName) {
        this.originMapperBeanName = originMapperBeanName;
    }

    public void setDispatchOriginMapperMappings(Map<String, String> dispatchOriginMapperMappings) {
        this.dispatchOriginMapperMappings = dispatchOriginMapperMappings;
    }

    public void setDispatchImplMapperMappings(Map<String, String> dispatchImplMapperMappings) {
        this.dispatchImplMapperMappings = dispatchImplMapperMappings;
    }

    public void setDispatchTo(String dispatchTo) {
        this.dispatchTo = dispatchTo;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getObject() {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{mapperInterface},
                new RoutingMapperInvocationHandler(
                        this.mapperInterface,
                        this.originMapperBeanName,
                        this.dispatchOriginMapperMappings,
                        this.dispatchImplMapperMappings,
                        this.beanFactory,
                        this.dispatchTo
                ));
    }

    @Override
    public Class<?> getObjectType() {
        return mapperInterface;
    }

}
