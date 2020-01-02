## 1. 引入依赖包

```java
// https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-web
compile group: 'org.springframework.boot', name: 'spring-boot-starter-web', version: '2.1.1.RELEASE'

// https://mvnrepository.com/artifact/org.activiti/activiti-spring-boot-starter-basic
compile group: 'org.activiti', name: 'activiti-spring-boot-starter-basic', version: '6.0.0'

// https://mvnrepository.com/artifact/com.oracle.ojdbc/ojdbc8
compile group: 'com.oracle.ojdbc', name: 'ojdbc8', version: '19.3.0.0'

// https://mvnrepository.com/artifact/org.mybatis.spring.boot/mybatis-spring-boot-starter
compile group: 'org.mybatis.spring.boot', name: 'mybatis-spring-boot-starter', version: '2.0.1'

// https://mvnrepository.com/artifact/org.projectlombok/lombok
providedCompile group: 'org.projectlombok', name: 'lombok', version: '1.18.8'
```

> 注：由于 springboot 2.x 可能会与引入的 activiti 6.0 有冲突，导致报错如下：
> `org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'requestMappingHandlerMapping' defined in class path resource [org/springframework/boot/autoconfigure/web/servlet/WebMvcAutoConfiguration$EnableWebMvcConfiguration.class]: Invocation of init method failed; nested exception is java.lang.ArrayStoreException: sun.reflect.annotation.TypeNotPresentExceptionProxy` 
> 这个时候在springboot的启动类上加上
> `@SpringBootApplication
        (exclude = {org.activiti.spring.boot.SecurityAutoConfiguration.class})` 就好了。


## 2. 配置application.yml文件

```yaml
server:
  port: 8081
spring:
  datasource:
    driver-class-name: oracle.jdbc.driver.OracleDriver
    url: jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST = 127.0.0.1)(PORT = 1521))(CONNECT_DATA =(SERVER = DEDICATED)(SERVICE_NAME = orcl)))
    username: scott
    password: tiger
  activiti:
    # 自动建表
    #database-schema: ACTIVITI # TODO 千万不要加这个（第一次运行可以加上，创建好后可以注释掉）
    #    false （默认值）：在创建流程引擎时检查库模式的版本，如果版本不匹配则抛出异常。
    #    true：在创建流程引擎时，执行检查并在必要时对数据库中所有的表进行更新，如果表不存在，则自动创建。
    #    create-drop：在创建流程引擎时，会创建数据库的表，并在关闭流程引擎时删除数据库的表。
    #    drop-create：Activiti启动时，执行数据库表的删除操作，在Activiti关闭时，会执行数据库表的创建操作。
    database-schema-update: false # 自动更新数据库结构
    #    none：不保存任何的历史数据，因此，在流程执行过程中，这是最高效的。
    #    activity：级别高于none，保存流程实例与流程行为，其他数据不保存。
    #    audit：除activity级别会保存的数据外，还会保存全部的流程任务及其属性。audit为history的默认值。
    #    full：保存历史数据的最高级别，除了会保存audit级别的数据外，还会保存其他全部流程相关的细节数据，包括一些流程参数等。
    history-level: full # 保存历史数据库级别为full最高级别，便于历史数据的追溯
    db-history-used: true
    check-process-definitions: false # TODO 自动检查部署流程定义文件。默认为true，自动创建好表之后设为false。设为false会取消自动部署功能。
    copy-variables-to-local-for-tasks: false # 不加这行配置的话，运行时流程变量表 + 历史流程变量表 统统的多出来一倍数据（其中taskId为null）
mybatis:
  # xml位置，指定dao与sql关联
  mapper-locations: classpath:mapper/*.xml
  configuration:
    # 下划线命名的字段，自动转驼峰命名
    map-underscore-to-camel-case: true
    default-executor-type: batch
  type-aliases-package: com.caihao.activititest2.entity
```

## 3. 画流程图
画流程图的话可以通过 idea 软件装插件的方式进行画图，也可以去官网下载 demo，然后将 activiti-app.war 放到 tomcat 下启动，访问 [http://localhost:8080/activiti-app/editor/#/processes](http://localhost:8080/activiti-app/editor/#/processes) 进行画图。
下面是我运行官网 demo 所画的请假流程图。
![请假单流程图](https://img-blog.csdnimg.cn/20200102140653540.jpg?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NoaW5lc2VfY2Fp,size_16,color_FFFFFF,t_70)
将画好的流程图导出，放到项目的 resources 资源目录下的 processes 目录下。
>注：流程图不是必须放在 processes 目录下，只是好像 resources 目录下必须得有 processes 目录，因为 activiti 默认初始化时会去加载 processes 目录，如果没有 processes 目录的话好像是会报错的。因此，画好的流程图只需放在 resources 目录下即可，但是 resources 目录下必须要有 processes 文件夹。

## 4. 编写代码
### 4.1 部署流程定义

首先部署流程定义，repositoryService 通过 springboot 注入的方式得到。

```java
public String deployProcess() {
    Deployment deployment = repositoryService.createDeployment()
            .addClasspathResource("processes/leaveBill2.bpmn20.xml")
            .name("请假审批")
            .category("办公类别")
            .deploy();
    log.info("deploymentId:{};deploymentName:{}", deployment.getId(), deployment.getName());
    return "deploy success";
}
```

### 4.2 删除流程定义
创建流程定义之后可能会觉得没创建好，想重新再来。这个时候就需要删除流程定义了。

```java
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
```
>由于重复部署流程定义会产生多个版本的流程定义，因此这里选择通过指定 key 获取对应的流程定义集合，然后循环删除。

### 4.3 启动流程实例
部署流程定义之后，紧接着就是要启动流程了。runtimeService 通过注入的方式得到。

```java
public String startProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("userId", "张三");
    // 启动流程，同时设置变量（因为第一个任务节点的指定人是通过变量的方式获取的）
    ProcessInstance pi = runtimeService.startProcessInstanceByKey("leaveBill2", variables);
    log.info("pid:{}={}", pi.getId(), pi.getProcessInstanceId());
    return "pid:" + pi.getId();
}
```

### 4.4 删除流程实例
有时候，可能运行着的流程被人取消了，我们就需要删除这个流程实例。

```java
public String deleteProcessInstance(String piId, String reason) {
    runtimeService.deleteProcessInstance(piId, reason);
    return "piId:" + piId + "; reason:" + reason;
}
```
>注：删除流程实例和删除流程定义是不同的。删除流程实例就好比把一个 class 的对象给置为 null 了，我们还可以重新 new 一个对象出来（对应着启动流程实例）。而删除流程定义则直接把这个 class 文件给删除掉了。

### 4.5 获取任务
已经启动流程实例了，这个时候可以查看某人的任务了。taskService 通过注入的方式得到。

```java
public String getTask(String userId) {
    List<Task> taskList = taskService.createTaskQuery()// 创建任务查询对象
            .taskAssignee(userId)// 指定个人任务办理人
            .list();
    for (Task task : taskList) {
        log.info("taskId:{};taskName:{};pid:{}", task.getId(), task.getName(), task.getProcessInstanceId());
    }
    return taskList.toString();
}
```
>这里可以获取到任务 id 和 流程实例 id，任务 id 可以用来完成任务，流程实例 id 可以用来查看流程图等。

### 4.6 完成任务
完成任务的时候需要用到任务 id，我们可以通过上面的获取任务来获得任务 id。
```java
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
```

### 4.7 查看流程执行图
由于这块代码较多，就不展示出来了。这里截取一张效果图，内容比较简陋。
![流程执行图](https://img-blog.csdnimg.cn/20200102151847639.jpg?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NoaW5lc2VfY2Fp,size_16,color_FFFFFF,t_70)

---
至此，springboot + activiti 的项目搭建就结束了。内容写的比较简单，主要是记录一些步骤，方便复习。



