package net.bytebuddy.test;

import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;

/**
 * <p>
 * Description: TestPlugin
 * </p>
 * <p>
 * Copyright: 2017
 * </p>
 *
 * @author Denom
 * @version 1.0
 */
public class TestPlugin implements Plugin {
    
    @Override
    public boolean matches(TypeDescription type) {
        return true;
    }
    
    @Override
    public Builder<?> apply(Builder<?> builder, TypeDescription type) {
        System.out.println("Transforming: " + type);
        return builder;
    }
    
}
