package io.github.abaafdec.mybatis.routing.annontations;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface RoutingMapper {

    String[] value() default {};

    DbType[] dbType();

    enum DbType {
        postgresql,
        mysql,
    }
}
