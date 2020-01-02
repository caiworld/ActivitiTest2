package com.caihao.activititest2.controller;

import com.caihao.activititest2.util.ActivitiUtils2;
import lombok.extern.slf4j.Slf4j;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricActivityInstanceQuery;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.image.ProcessDiagramGenerator;
import org.activiti.image.impl.DefaultProcessDiagramGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 请假流程
 * create by caihao on 2019/12/31
 */
@RestController
@RequestMapping("/leaveBill")
@Slf4j
public class LeaveBillController {

    private static final String BPMN = "processes/leaveBill2.bpmn20.xml";

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private HistoryService historyService;

    /**
     * 部署流程定义
     *
     * @return java.lang.String
     * @date 2020/1/2 9:32
     * @since 1.0.0
     */
    @RequestMapping("/deploy")
    public String deployProcess() {
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource(BPMN)
                .name("请假审批")
                .category("办公类别")
                .deploy();
        log.info("deploymentId:{};deploymentName:{}", deployment.getId(), deployment.getName());
        return "deploy success";
    }

    /**
     * 删除流程定义
     *
     * @return java.lang.String
     * @date 2020/1/2 10:35
     * @since 1.0.0
     */
    @RequestMapping("deletePD")
    public String deleteProcessDefinition() {
        // 根据流程定义的key删除流程定义
        // 查询指定key的所有版本的流程定义
        List<ProcessDefinition> pdList = repositoryService.createProcessDefinitionQuery().processDefinitionKey
                ("leaveBill2").list();
        for (ProcessDefinition processDefinition : pdList) {
            // 级联删除指定key的所有版本
            repositoryService.deleteDeployment(processDefinition.getDeploymentId(), true);
        }
        return "delete success";
    }

    /**
     * 启动流程实例
     *
     * @return java.lang.String
     * @date 2020/1/2 9:32
     * @since 1.0.0
     */
    @RequestMapping("/start")
    public String startProcess() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", "张三");
        // 启动流程，同时设置变量（因为第一个任务节点的指定人是通过变量的方式获取的）
        ProcessInstance pi = runtimeService.startProcessInstanceByKey("leaveBill2", variables);
        log.info("pid:{}={}", pi.getId(), pi.getProcessInstanceId());
        return "pid:" + pi.getId();
    }

    /**
     * <p>查看当前流程图</p>
     *
     * @param instanceId 流程实例
     * @param response   void 响应
     * @author FRH
     * @time 2018年12月10日上午11:14:12
     * @version 1.0
     */
    @RequestMapping(value = "/showImg")
    public void showImg(String instanceId, HttpServletResponse response) {
        /*
         * 参数校验
		 */
        log.info("查看完整流程图！流程实例ID:{}", instanceId);
        if (StringUtils.isBlank(instanceId)) return;

		/*
         *  获取流程实例
		 */
        HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult();
        if (processInstance == null) {
            log.error("流程实例ID:{}没查询到流程实例！", instanceId);
            return;
        }

        // 根据流程对象获取流程对象模型
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());

//        Task task = taskService.createTaskQuery().processInstanceId(instanceId).singleResult();
//        String processDefinitionId = task.getProcessDefinitionId();
//        System.out.println(processDefinitionId);
//        System.out.println(processInstance.getProcessDefinitionId());

		/*
         *  查看已执行的节点集合
		 *  获取流程历史中已执行节点，并按照节点在流程中执行先后顺序排序
		 */
        // 构造历史流程查询
        HistoricActivityInstanceQuery historyInstanceQuery = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId);
        // 查询历史节点
        List<HistoricActivityInstance> historicActivityInstanceList = historyInstanceQuery
                .orderByHistoricActivityInstanceStartTime().asc().list();
        if (historicActivityInstanceList == null || historicActivityInstanceList.size() == 0) {
            log.info("流程实例ID:{}没有历史节点信息！", instanceId);
            outputImg(response, bpmnModel, null, null);
            return;
        }
        // 已执行的节点ID集合(将historicActivityInstanceList中元素的activityId字段取出封装到executedActivityIdList)
        List<String> executedActivityIdList = historicActivityInstanceList.stream().map(item -> item.getActivityId())
                .collect(Collectors.toList());

		/*
         *  获取流程走过的线
		 */
        // 获取流程定义
        ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) ((RepositoryServiceImpl)
                repositoryService).getDeployedProcessDefinition(processInstance.getProcessDefinitionId());
        List<HistoricVariableInstance> hviList = historyService.createHistoricVariableInstanceQuery().processInstanceId
                (instanceId).list();
        List<String> flowIds = ActivitiUtils2.getHighLightedFlows(bpmnModel, processDefinition,
                historicActivityInstanceList, hviList);

		/*
         * 输出图像，并设置高亮
		 */
        outputImg(response, bpmnModel, flowIds, executedActivityIdList);
    }

    /**
     * <p>输出图像</p>
     *
     * @param response               响应实体
     * @param bpmnModel              图像对象
     * @param flowIds                已执行的线集合
     * @param executedActivityIdList void 已执行的节点ID集合
     * @author FRH
     * @time 2018年12月10日上午11:23:01
     * @version 1.0
     */
    private void outputImg(HttpServletResponse response, BpmnModel bpmnModel, List<String> flowIds, List<String>
            executedActivityIdList) {
        InputStream imageStream = null;
        try {
            ProcessDiagramGenerator processDiagramGenerator = new DefaultProcessDiagramGenerator();
            imageStream = processDiagramGenerator.generateDiagram(bpmnModel, "png", executedActivityIdList,
                    flowIds, "宋体", "微软雅黑", "黑体", null,
                    1.0);
            // 输出资源内容到相应对象
            byte[] b = new byte[1024];
            int len;
            while ((len = imageStream.read(b, 0, 1024)) != -1) {
                response.getOutputStream().write(b, 0, len);
            }
//            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("流程图输出异常！", e);
        } finally { // 流关闭
//            IOUtils.closeInputStream(imageStream);
            try {
                imageStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 根据流程实例id删除流程实例
     *
     * @param piId   流程实例id
     * @param reason 删除原因
     * @return java.lang.String
     * @date 2020/1/2 10:45
     * @since 1.0.0
     */
    @RequestMapping("/deletePI")
    public String deleteProcessInstance(String piId, String reason) {
        runtimeService.deleteProcessInstance(piId, reason);
        return "piId:" + piId + "; reason:" + reason;
    }


    /**
     * 根据用户id获取用户任务
     *
     * @param userId 用户id
     * @return java.lang.String
     * @date 2020/1/2 9:54
     * @since 1.0.0
     */
    @RequestMapping("/getTask")
    public String getTask(String userId) {
        List<Task> taskList = taskService.createTaskQuery()// 创建任务查询对象
                .taskAssignee(userId)// 指定个人任务办理人
                .list();
        for (Task task : taskList) {
            log.info("taskId:{};taskName:{};pid:{}", task.getId(), task.getName(), task.getProcessInstanceId());
        }
        return taskList.toString();
    }

    /**
     * 完成任务
     *
     * @param userId 用户id
     * @param taskId 任务id
     * @param agree  意见
     * @return java.lang.String
     * @date 2020/1/2 10:34
     * @since 1.0.0
     */
    @RequestMapping("/complete")
    public String completeTask(String userId, String taskId, String agree) {
        Map<String, Object> variables = new HashMap<>();
        if ("张三".equals(userId)) {
            variables.put("agree1", agree);
        } else if ("李四".equals(userId)) {
            variables.put("agree2", agree);
        }
        // 完成任务，同时设置变量（因为我的流程图里面，通过agree1和agree2变量的值来决定具体走哪条线）
        taskService.complete(taskId, variables);
        return "complete success";
    }

}
