package com.alibaba.cloud.ai.studio.admin.service.client;

import com.alibaba.cloud.ai.studio.admin.entity.ModelConfigDO;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.Map;

public interface ChatClientFactory {
    
    String getSupportProvider();
    
    ChatModel buildChatModel(ModelConfigDO modelConfig);

    ChatOptions buildChatOptions(ModelConfigDO modelConfig,Map<String, Object> userParameters, Map<String, String> observationMetadata);
    
}
