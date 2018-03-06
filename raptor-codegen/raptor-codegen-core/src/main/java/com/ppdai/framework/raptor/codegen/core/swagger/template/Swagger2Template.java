package com.ppdai.framework.raptor.codegen.core.swagger.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DescriptorProtos;
import com.ppdai.framework.raptor.codegen.core.swagger.container.EnumContainer;
import com.ppdai.framework.raptor.codegen.core.swagger.container.MessageContainer;
import com.ppdai.framework.raptor.codegen.core.swagger.container.MetaContainer;
import com.ppdai.framework.raptor.codegen.core.swagger.container.ServiceContainer;
import com.ppdai.framework.raptor.codegen.core.swagger.swagger2object.*;
import com.ppdai.framework.raptor.codegen.core.swagger.tool.ContainerUtil;
import com.ppdai.framework.raptor.codegen.core.swagger.tool.TypeFormatUtil;
import com.ppdai.framework.raptor.codegen.core.swagger.type.*;
import com.ppdai.framework.raptor.codegen.core.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by zhangyicong on 18-2-27.
 */
public class Swagger2Template implements SwaggerTemplate {

    private static final Logger logger = LoggerFactory.getLogger(SwaggerTemplate.class);

    private final ObjectMapper mapper;

    public Swagger2Template() {
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 生成swagger service定义
     *
     * @param swaggerObject
     * @param serviceContainer
     */
    private List<MessageType> renderServices(SwaggerObject swaggerObject,
                                            ServiceContainer serviceContainer,
                                            MetaContainer metaContainer,
                                            String basePackage) {

        List<MessageType> dependMessage = new ArrayList<>();

        for (ServiceType serviceType : serviceContainer.getServiceTypeList()) {
            for (MethodType methodType : serviceType.getMethodTypeList()) {
                SwaggerPathItemObject swaggerPathItemObject = new SwaggerPathItemObject();

                // set post operation
                SwaggerOperationObject swaggerOperationObject = new SwaggerOperationObject();
                swaggerPathItemObject.setPost(swaggerOperationObject);
                swaggerOperationObject.setDescription("");// todo
                swaggerOperationObject.setSummary("");// todo
                swaggerOperationObject.setOperationId(methodType.getName());

                // set consumes
                swaggerOperationObject.setConsumes(swaggerObject.getConsumes());
                // set produces
                swaggerOperationObject.setProduces(swaggerObject.getProduces());

                // set parameters
                List<SwaggerParameterObject> parameters = new ArrayList<>();
                swaggerOperationObject.setParameters(parameters);
                SwaggerParameterObject swaggerParameterObject = new SwaggerParameterObject();
                parameters.add(swaggerParameterObject);

                swaggerParameterObject.setDescription("");// todo
                swaggerParameterObject.setIn("body");
                swaggerParameterObject.setName("body");
                swaggerParameterObject.setRequired(true);

                // set schema
                SwaggerSchemaObject swaggerSchemaObject = new SwaggerSchemaObject();
                MessageType inputMessage = metaContainer.findMessageTypeByFQPN(methodType.getInputType(), basePackage);
                dependMessage.add(inputMessage);
                swaggerSchemaObject.setRef("#/definitions/" +
                        getRefName(inputMessage, basePackage));
                swaggerParameterObject.setSchema(swaggerSchemaObject);

                // set responses
                Map<String, SwaggerResponseObject> responses = new LinkedHashMap<>();
                SwaggerResponseObject swaggerResponseObject = new SwaggerResponseObject();
                swaggerResponseObject.setDescription("successful operation");
                // set responses schema
                SwaggerSchemaObject reponseSchema = new SwaggerSchemaObject();
                swaggerResponseObject.setSchema(reponseSchema);
                MessageType outputMessage = metaContainer.findMessageTypeByFQPN(methodType.getOutputType(), basePackage);
                dependMessage.add(outputMessage);
                reponseSchema.setRef("#/definitions/" +
                        getRefName(outputMessage, basePackage));

                responses.put("200", swaggerResponseObject);
                swaggerOperationObject.setResponses(responses);

                swaggerObject.getPaths().put("/raptor/" + serviceType.getFQPN() + "/" + methodType.getName(),
                        swaggerPathItemObject);
            }
        }
        return dependMessage;
    }

    private String getRefName(MessageType inputMessage, String basePackage) {
        String packageName = CommonUtils.getPackageNameFromFQPN(inputMessage.getFQPN());
        if (basePackage.equals(packageName)) {
            return inputMessage.getName();
        } else {
            return inputMessage.getFQPN();
        }
    }

    private void addEnum2Definitions(SwaggerObject swaggerObject, EnumType enumType) {
        SwaggerSchemaObject swaggerSchemaObject = new SwaggerSchemaObject();
        swaggerObject.getDefinitions().put(enumType.getName(), swaggerSchemaObject);

        swaggerSchemaObject.setType("string");
        swaggerSchemaObject.setSwaggerEnum(new ArrayList<>(enumType.getValues()));
    }

    private void addType2Definitions(SwaggerObject swaggerObject, MessageType messageType, String basePackage) {
        SwaggerSchemaObject swaggerSchemaObject = new SwaggerSchemaObject();
        swaggerObject.getDefinitions().put(getRefName(messageType, basePackage), swaggerSchemaObject);

        swaggerSchemaObject.setType("object");

        Map<String, Object> properties = new LinkedHashMap<>();
        swaggerSchemaObject.setProperties(properties);

        for (FieldType fieldType : messageType.getFieldTypeList()) {
            Map<String, Object> typeSchema = TypeFormatUtil.formatTypeSwagger2(fieldType);
            properties.put(fieldType.getName(), typeSchema);
        }
    }

    /**
     * 生成swagger type定义
     *
     * @param swaggerObject
     * @param messageTypes
     * @param enumContainer
     */
    private void renderDefinitions(SwaggerObject swaggerObject,
                                   List<MessageType> messageTypes,
                                   EnumContainer enumContainer,
                                   String basePackage) {
        // TODO: 2018/3/6 需要过滤,吧不需要的 definition 去掉
        // TODO: 2018/3/6 验证不同包之间的应用是否会出问题
        for (MessageType messageType : messageTypes) {
            addType2Definitions(swaggerObject, messageType,basePackage);
        }

        for (EnumType enumType : enumContainer.getEnumTypeList()) {
            addEnum2Definitions(swaggerObject, enumType);
        }
    }

    /**
     * proto转swagger
     *
     * @param fdp
     * @param apiVersion
     * @return
     */
    public String applyTemplate(DescriptorProtos.FileDescriptorProto fdp,
                                MetaContainer metaContainer,
                                String apiVersion) throws JsonProcessingException {
        // 从pb中提取enum
        EnumContainer enumContainer = ContainerUtil.getEnums(fdp, metaContainer);
        // 从pb中提取message
        MessageContainer messageContainer = ContainerUtil.getMessages(fdp, metaContainer);
        // 从pb中提取service
        ServiceContainer serviceContainer = ContainerUtil.getServiceContainer(fdp);

        SwaggerObject swaggerObject = new SwaggerObject("2.0",
                Arrays.asList("http"),
                Arrays.asList("application/json"),
                Arrays.asList("application/json"),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new SwaggerInfoObject(fdp.getName(), apiVersion));

        String basePackage = fdp.getPackage();
        // render path
        List<MessageType> messageTypes = renderServices(swaggerObject, serviceContainer, metaContainer, basePackage);
        // render type and enum
        renderDefinitions(swaggerObject, messageTypes, enumContainer,basePackage);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(swaggerObject);
    }
}