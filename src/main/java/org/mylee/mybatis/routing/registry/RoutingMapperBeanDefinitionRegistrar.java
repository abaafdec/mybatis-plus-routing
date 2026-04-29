package org.mylee.mybatis.routing.registry;

import org.mylee.mybatis.routing.annontations.Routing;
import org.mylee.mybatis.routing.annontations.RoutingMapper;
import org.mylee.mybatis.routing.proxy.RoutingMapperFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class RoutingMapperBeanDefinitionRegistrar implements BeanFactoryPostProcessor, PriorityOrdered {


    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            return;
        }

        String[] beanDefinitionNames = beanFactory.getBeanDefinitionNames();

        Map<String, Map<String, MapperEntry>>   mapperVariants     = new HashMap<>();
        Map<String, Map<String, String>>        customImplVariants = new HashMap<>();

        for (String bdName : beanDefinitionNames) {
            BeanDefinition bd = registry.getBeanDefinition(bdName);

            String mapperItfName = resolveMapperInterface(bd);
            if (mapperItfName != null) {
                Class<?> itf = loadClass(mapperItfName);
                if (itf == null) {
                    continue;
                }
                RoutingMapper rm = AnnotationUtils.findAnnotation(itf, RoutingMapper.class);
                if (rm == null) continue;

                String logicalId = rm.value().length == 0 ? itf.getSimpleName() : rm.value()[0];
                String dbType = rm.dbType() == null || rm.dbType().length == 0 ? null : rm.dbType()[0];
                if (dbType == null) {
                    throw new IllegalArgumentException();
                }

                mapperVariants.computeIfAbsent(logicalId, k -> new HashMap<>())
                        .put(dbType, new MapperEntry(bdName, itf));
                continue;
            }

            Class<?> customerImplClass = loadClass(bd.getBeanClassName());
            if (customerImplClass != null) {
                RoutingMapper rm = AnnotationUtils.findAnnotation(customerImplClass, RoutingMapper.class);
                if (rm == null) continue;

                String logicalId = rm.value().length == 0 ? null : rm.value()[0];
                String dbType = rm.dbType() == null || rm.dbType().length == 0 ? null : rm.dbType()[0];
                if (logicalId == null || dbType == null) {
                    throw new IllegalArgumentException();
                }

                customImplVariants.computeIfAbsent(logicalId, k -> new HashMap<>())
                        .put(dbType, bdName);
            }
        }


        for (String bdName : beanDefinitionNames) {
            BeanDefinition bd = registry.getBeanDefinition(bdName);
            if (!isOriginalMapperBeanDefinition(bd)) {
                continue;
            }

            String routingBeanName = buildRoutingBeanName(bdName);
            if (registry.containsBeanDefinition(routingBeanName)) {
                continue;
            }


            String mapperItfName = resolveMapperInterface(bd);


            Class<?> mapperInterface = loadClass(mapperItfName);
            if (mapperInterface == null) {
                continue;
            }

            RoutingMapper rm = AnnotationUtils.findAnnotation(mapperInterface, RoutingMapper.class);
            if (rm == null) continue;
            String logicalId = rm.value().length == 0 ? mapperInterface.getSimpleName() : rm.value()[0];

            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(RoutingMapperFactoryBean.class);
            beanDefinitionBuilder.addPropertyValue("mapperInterface", mapperInterface);
            beanDefinitionBuilder.addPropertyValue("originMapperBeanName", bdName);

            Map<String, MapperEntry> logical_patch = mapperVariants.get(logicalId);
            Map<String, String> dispatchOriginMapperMappings = logical_patch.entrySet().stream().collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue().beanName()));

            beanDefinitionBuilder.addPropertyValue("dispatchOriginMapperMappings", dispatchOriginMapperMappings);
            beanDefinitionBuilder.addPropertyValue("dispatchImplMapperMappings", customImplVariants.get(logicalId));
            beanDefinitionBuilder.addPropertyValue("dispatchTo", "${org-mylee.mybatis-routing.dispatche-to}");


            AbstractBeanDefinition routingBd = beanDefinitionBuilder.getBeanDefinition();
            routingBd.setLazyInit(bd.isLazyInit());
            routingBd.setScope(bd.getScope());
            routingBd.setPrimary(false);
            routingBd.setAutowireCandidate(true);
            routingBd.setRole(BeanDefinition.ROLE_APPLICATION);


            routingBd.addQualifier(new AutowireCandidateQualifier(Routing.class));

            registry.registerBeanDefinition(routingBeanName, routingBd);

            bd.setPrimary(true);
        }
    }


    private String resolveMapperInterface(BeanDefinition bd) {
        String beanClassName = bd.getBeanClassName();
        if (beanClassName == null) return null;
        if (!"org.mybatis.spring.mapper.MapperFactoryBean".equals(beanClassName)) {
            return null;
        }

        ConstructorArgumentValues cav = bd.getConstructorArgumentValues();
        ConstructorArgumentValues.ValueHolder vh = cav.getGenericArgumentValue(Class.class);
        if (vh != null) {
            Object v = vh.getValue();
            if (v instanceof Class<?> c) return c.getName();
            if (v instanceof String s)   return s;
        }

        PropertyValue pv = bd.getPropertyValues().getPropertyValue("mapperInterface");
        if (pv != null) {
            Object v = pv.getValue();
            if (v instanceof Class<?> c) return c.getName();
            if (v instanceof String s)   return s;
        }
        return null;
    }

    private Class<?> loadClass(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ClassUtils.forName(name, getClass().getClassLoader());
        } catch (ClassNotFoundException | IllegalArgumentException e) {
            return null;
        }
    }

    private record MapperEntry(String beanName, Class<?> mapperInterface) {}

    private boolean isOriginalMapperBeanDefinition(BeanDefinition bd) {
        String beanClassName = bd.getBeanClassName();
        if ("org.mybatis.spring.mapper.MapperFactoryBean".equals(beanClassName)) {
            return true;
        }
        return bd.getPropertyValues().get("mapperInterface") != null;
    }

    private String buildRoutingBeanName(String originalBeanName) {
        return originalBeanName + "$routing";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
