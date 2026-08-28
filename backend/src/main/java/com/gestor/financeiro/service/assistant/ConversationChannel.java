package com.gestor.financeiro.service.assistant;

public interface ConversationChannel {
    void receive(String externalEventId);
    void send(Long conversationId, String message);
}
