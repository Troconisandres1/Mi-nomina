package org.springframework.ai.tool.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método Java como una herramienta (Tool) que Spring AI puede exponer
 * al modelo de lenguaje (LLM) dentro del mecanismo de Tool Calling.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tool {

    /**
     * Nombre opcional de la herramienta. Si se omite, se utiliza el nombre del método Java.
     */
    String name() default "";

    /**
     * Descripción semántica de la herramienta que orienta al LLM sobre cuándo
     * debe seleccionarla y con qué propósito.
     */
    String description() default "";
}
