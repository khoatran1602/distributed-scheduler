package com.demo.scheduler.service.broker;

import com.demo.scheduler.model.Task;

public interface TaskBroker {
    void submitTask(Task task);
    String getBrokerType();
}
