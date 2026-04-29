package org.mylee.mybatis.routing.proxy;

import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingMapperInvocationHandler implements InvocationHandler {

    private final Class<?> mapperInterface;
    private final String originMapperBeanName;
    private final BeanFactory beanFactory;

    private final Map<String, String> mapperBeanNameMappings;
    private final Map<String, String> customImplBeanNameMappings;

    private final Map<String, Object> mapperCache = new ConcurrentHashMap<>();
    private final Map<String, Object> customImplCache = new ConcurrentHashMap<>();

    private String dispatchTo;


    public RoutingMapperInvocationHandler(Class<?> mapperInterface,
                                          String originMapperBeanName,
                                          Map<String, String> mapperBeanNameMappings,
                                          Map<String, String> customImplBeanNameMappings,
                                          BeanFactory beanFactory,
                                          String dispatchTo) {
        this.mapperInterface = mapperInterface;
        this.originMapperBeanName = originMapperBeanName;
        this.mapperBeanNameMappings = mapperBeanNameMappings;
        this.customImplBeanNameMappings = customImplBeanNameMappings;
        this.beanFactory = beanFactory;
        this.dispatchTo = dispatchTo;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }

        if (method.isDefault()) {
            InvocationHandler.invokeDefault(proxy, method, args);
        }

        // current db type
        String dbType = this.dispatchTo;
        Object dispatch_impl_obj = resolveDispatchImpl(dbType);
        if (dispatch_impl_obj != null) {
            return invokeCompatibleMethod(dispatch_impl_obj, method, args);
        }

        Object dispatchMapper = resolveDispatchMapper(dbType);

        return invokeCompatibleMethod(dispatchMapper, method, args);
    }

    private Object invokeCompatibleMethod(Object target, Method sourceMethod, Object[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (sourceMethod.getDeclaringClass().isInstance(target)) {
            return sourceMethod.invoke(target, args);
        }


        Method targetMethod = findCompatibleMethod(target.getClass(), sourceMethod);
        return targetMethod.invoke(target, args);
    }

    private Object resolveDispatchMapper(String dbType) {
        String beanName = null;
        if (mapperBeanNameMappings != null) {
            beanName = mapperBeanNameMappings.get(dbType);
        }
        if (beanName == null) {
            beanName = originMapperBeanName;
        }
        String finalBeanName = beanName;
        return mapperCache.computeIfAbsent(finalBeanName, beanFactory::getBean);
    }

    private Object resolveDispatchImpl(String dbType) {
        if (customImplBeanNameMappings == null) {
            return null;
        }
        String beanName = customImplBeanNameMappings.get(dbType);
        if (beanName == null) {
            return null;
        }
        return customImplCache.computeIfAbsent(beanName, beanFactory::getBean);
    }

    private Method findCompatibleMethod(Class<?> targetClass, Method sourceMethod) throws NoSuchMethodException {
        return targetClass.getMethod(
                sourceMethod.getName(),
                sourceMethod.getParameterTypes()
        );
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> mapperInterface.getName() + "$RoutingMapperProxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }
}
