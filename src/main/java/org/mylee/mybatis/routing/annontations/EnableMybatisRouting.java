package org.mylee.mybatis.routing.annontations;

import org.mylee.mybatis.routing.registry.RoutingMapperBeanDefinitionRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(RoutingMapperBeanDefinitionRegistrar.class)
public @interface EnableMybatisRouting {
}
