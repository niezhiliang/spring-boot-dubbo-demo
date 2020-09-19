### 角色

#### Register

#### Providere

#### Container

#### Consumer

#### Monitor

Dubbo-admin(http://127.0.0.1:9999/#/),这是dubbo官方提供的一个服务监控，

主要功能是监控服务端(Provider)和消费端(Consumer)的使用数据的. 如: 服务端是什么,有多少接口,多少方法, 调用次数, 压力信息等. 客户端有多少, 调用过哪些服务端, 调用了多少次等.

### 执行流程

![img](http://dubbo.apache.org/img/architecture.png)

- start：启动spring容器时，自动启动Dubbo的Provider
- register：Dubbo的Provider在启动后自动向注册中心注册内容
  - Provider的IP
  - Provider的port
  - Provider对外提供的接口列表（方法，接口类）
  - Dubbo的版本
  - 访问Provider的协议
- subscribe: 当Consumer启动时,自动去Registry获取到所已注册的服务的信息
- notify: 当Provider的信息发生变化时, 自动由Registry向Consumer推送通知.
- invoke: Consumer 调用Provider中方法
  - 同步请求消耗一定性能.但是必须是同步请求,因为需要接收调用方法后的结果
- count: 每隔2分钟,Provoider和Consumer自动向Monitor发送访问次数Monitor进行统计

服务暴露

- 封装成一个Invoker
- Invoker包装成一个Exporter



ProxyFactory：为了获取一个接口的代理类，例如获取一个远程接口的代理，一共有两个方法，代表两个作用：

a.getInvoker:针对服务端，将服务对象，如SayServiceImpl包装成一个Invoker对象

b.getProxy：针对客户端，创建接口的代理对象，例如SayService的接口



Wrapper:它类似于spring的BeanWrapper,它就是包装了一个接口或一个类，可以通过wrapper对实力对象进行赋值以及制定方法的调用

Invoker:它是一个可执行的对象，能够根据方法的名称、参数得到相应的执行结果。它里面有一个很重要的方法Result invoke(Invocation invocation),Invocation是包含了需要执行的方法和参数等重要信息，目前它只有2个实现类,RpcInvocation  MockInvocation,它有3种类型的Invoker

1.本地执行的Invoker

2.远程通讯执行类的Invoker聚合成集群版的invoker

```java
//监听了容器启动完成事件
-->org.apache.dubbo.config.spring.ServiceBean#onApplicationEvent
   -->org.apache.dubbo.config.spring.ServiceBean#export
   		-->org.apache.dubbo.config.ServiceConfig#export
   			 -->org.apache.dubbo.config.ServiceConfig#checkAndUpdateSubConfigs
   			 -->org.apache.dubbo.config.ServiceConfig#doExport
   			 		-->org.apache.dubbo.config.ServiceConfig#doExportUrls
   			 			 -->org.apache.dubbo.config.AbstractInterfaceConfig#loadRegistries
   			 			 -->org.apache.dubbo.config.ServiceConfig#doExportUrlsFor1Protocol
  							//本地暴露
   			 			 	  -->org.apache.dubbo.config.ServiceConfig#exportLocal
   			 			 	  	 -->org.apache.dubbo.rpc.ProxyFactory#getInvoker
   			 			 	  	   -->org.apache.dubbo.common.extension.ExtensionLoader#getExtensionLoader
   			 			 	  	 	 -->org.apache.dubbo.common.extension.ExtensionLoader#getExtension
   			 			 	  	 	 -->org.apache.dubbo.rpc.ProxyFactory#getInvoker
   			 			 	  	 	 	  -->org.apache.dubbo.rpc.proxy.wrapper.StubProxyFactoryWrapper#getInvoker
   			 			 	  	 	 	  	 -->org.apache.dubbo.rpc.proxy.javassist.JavassistProxyFactory#getInvoker
   			 			 	  	 	 	  	 	  -->org.apache.dubbo.common.bytecode.Wrapper#getWrapper
   			 			 	  	 	 	  	 	  -->org.apache.dubbo.rpc.proxy.AbstractProxyInvoker#doInvoke
                    -->org.apache.dubbo.rpc.Protocol#export
                    	 -->org.apache.dubbo.rpc.Protocol$Adaptive#export
                          -->ExtensionLoader#getExtensionLoader
   			 			 	  	 	    -->org.apache.dubbo.common.extension.ExtensionLoader#getExtension
   			 			 	  	 	    -->org.apache.dubbo.rpc.Protocol#export
   			 			 	  	 	    	 -->org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper#export
   			 			 	  	 	    	 	  -->org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper#buildInvokerChain
   			 			 	  	 	    	 	  -->org.apache.dubbo.rpc.protocol.ProtocolListenerWrapper#export
   			 			 	  	 	    	 	  			//将服务放到一个exporterMap里面key为cn.isuyu.dubbo.demo.common.service.SayService:1.0 value为InjvmExporter
   			 			 	  	 	    	 	     -->org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol#export
    							//远程暴露
   			 			 	  -->org.apache.dubbo.config.ServiceConfig#exportLocal
                    	 
                    
   			 			 	  	 	 
   			 			 	  
```

