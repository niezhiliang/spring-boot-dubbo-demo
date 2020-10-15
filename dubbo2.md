# Dubbo分享

### dubbo总体架构

![img](https://img-blog.csdnimg.cn/img_convert/c666643ffa85deae03453e4b05f0dfce.png)

#### 角色

- Container：服务运行的容器（Spring）
- Register：注册中心（zk、redis等）
- Provider：服务提供方
- Consumer：需要远程调用的服务消费方
- Monitor：监控中心（dubbo-admin）

#### 执行流程

- start：启动spring容器时，自动启动Dubbo的Provider
- register：Dubbo的Provider在启动后自动向注册中心注册内容（ip、port、对外提供的方法接口、dubbo的版本）
- subscribe：当Consumer启动时，自动去register获取已经注册的服务信息
- invoke：Consumer调用Provider中的方法（同步请求消费一定性能，但是必须是同步请求，因为需要接收调用方法后的结果）
- count：每隔两分钟，Providre和Consumer自动向Monitor发送访问次数Monitor进行统计

### 分层架构

Dubbo总体分为三层，细分可以分为十层，如下图



![img](https://img-blog.csdnimg.cn/img_convert/d5931f8afd05403567240653433a1852.png)

- Service：业务层，也就是我们开发的业务逻辑层
- Config：配置层，主要围绕ServiceConfig和ReferenceConfig，初始化配置信息
- Proxy：代理层，服务提供者和消费者都会生成一个代理类，使得服务接口透明化，代理层做远程调用和返回结果
- Register：注册层，封装了服务注册和发现
- Cluster：路由和集群容错层，负责选取具体调用的节点，处理特殊调用请求和负责远程调用失败的容错措施
- Monitor：监控层，负责监控调用时间和次数
- Portocol：远程调用层，主要封装RPC调用，主要负责管理Invoker（代表一个抽象封装了的执行体）
- Exchange：信息交换层，用来封装请求响应模型，同步转异步
- Transport：网络传输层，抽象了网络传输的统一接口，这样用户想用Netty就用Netty，想用Mina就用Mina
- Serialize：系列化层，将数据序列化成二进制流，也可以反序列化

### SPI

SPI 全称为` Service Provider Interface`,是一种服务发现机制。简单来说就是一种约定，按照约定的特定配置，服务会去读取这些配置，加载配置中的类。我们通过接口就可以正常访问实现类的方法啦。

- Java SPI

Java SPI 就是这样做的，约定在 Classpath 下的 META-INF/services/ 目录里创建一个**以服务接口命名的文件**，然后**文件里面记录的是此 jar 包提供的具体实现类的全限定名**。

这样当我们引用了某个 jar 包的时候就可以去找这个 jar 包的 META-INF/services/ 目录，再根据接口名找到文件，然后读取文件里面的内容去进行实现类的加载与实例化。

eg:

 在META-INF/services创建一个以接口的全名称为文件名的配置文件（eg:spi.service.Robot），将需要加载的实现类全名称放在文件中

```java
spi.service.OptimusPrime
spi.service.Bumblebee
```

 通过SPI获取实现类的方式

```java
ServiceLoader<Robot> serviceLoader = ServiceLoader.load(Robot.class);
Iterator<Robot> iterator = serviceLoader.iterator();
while (iterator.hasNext()) {
    Robot robot = iterator.next();
    robot.sayHello();
}
```

如果配置了多个实现类，多个实现的方法会依次执行(这里我们配置了两个类，说明会执行两次sayHello())



Java SPI源码

```java
//1.获取当前线程的类加载器    
public static <S> ServiceLoader<S> load(Class<S> service) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    return ServiceLoader.load(service, cl);
}
//2创建了一个ServiceLoader对象
public static <S> ServiceLoader<S> load(Class<S> service,ClassLoader loader){
    return new ServiceLoader<>(service, loader);
}
//如果类加载器为空，直接用系统类加载器
private ServiceLoader(Class<S> svc, ClassLoader cl) {
    service = Objects.requireNonNull(svc, "Service interface cannot be null");
    loader = (cl == null) ? ClassLoader.getSystemClassLoader() : cl;
    acc = (System.getSecurityManager() != null) ? AccessController.getContext() : null;
    reload();
}
//清除缓存
public void reload() {
    providers.clear();
    lookupIterator = new LazyIterator(service, loader);//是Iterator的实现类
}
```

LazyIterator实现了这两个方法，不管是走if还是走else，重要的方法都是这两个 `hasNextService、nextService`

```java
public boolean hasNext() {
    if (acc == null) {
        return hasNextService();
    } else {
        PrivilegedAction<Boolean> action = new PrivilegedAction<Boolean>() {
            public Boolean run() { return hasNextService(); }
        };
        return AccessController.doPrivileged(action, acc);
    }
}

public S next() {
    if (acc == null) {
        return nextService();
    } else {
        PrivilegedAction<S> action = new PrivilegedAction<S>() {
            public S run() { return nextService(); }
        };
        return AccessController.doPrivileged(action, acc);
    }
}
```

```java
private boolean hasNextService() {
    if (nextName != null) {
        return true;
    }
    //第一次来获取接口的实现类
    if (configs == null) {
        try {
            //1.得到SPI配置的路径  META-INF/services/spi.service.Robot
            String fullName = PREFIX + service.getName();
            if (loader == null)
                //2.通过路径加载配置文件
                configs = ClassLoader.getSystemResources(fullName);
            else
                configs = loader.getResources(fullName);
        } catch (IOException x) {
        }
    }
    //3.while按行遍历文件内容
    while ((pending == null) || !pending.hasNext()) {
        if (!configs.hasMoreElements()) {
            return false;
        }
        pending = parse(service, configs.nextElement());
    }
    //4.得到的值复制给nextName
    nextName = pending.next();
    return true;
}

private S nextService() {
    if (!hasNextService()) throw new NoSuchElementException();
    //1.获取到上面设置的类全名 spi.service.OptimusPrime
    String cn = nextName;
    nextName = null;
    //2.通过类名加载指定类
    Class<?> c = Class.forName(cn, false, loader);
    if (!service.isAssignableFrom(c)) {
        fail(service,
             "Provider " + cn  + " not a subtype");
    }
    try {
        //3.反射得到实现类OptimusPrime
        S p = service.cast(c.newInstance());
        //4.放入缓存
        providers.put(cn, p);
        return p;
    } catch (Throwable x) {
    }
    throw new Error();          // This cannot happen
}
```

总结：

1.通过接口得到一个ServiceLoader

2.通过定义的接口去规定下的目录读取SPI的配置文件

3.通过while循环按行遍历配置文件得到实现类的全限定名称

4.通过类全限定名称反射获取接口的实现类，放入缓存中

- Dubbo SPI

Dubbo对配置文件目录的约定

 META-INF/dubbo用来存放用户自定义的SPI配置文件，META-INF/dubbo/internal用来存放Dubbo内部的SPI配置文件

需要注意的是，Dubbo中要实现SPI的接口必须加一个`@SPI`注解修饰，然后在META-INF/dubbo，创建一个和Java SPI中一样的配置文件，不过里面的内容有点不一样，以key和value的形式来配置

```java
optimusPrime = spi.service.OptimusPrime
bumblebee = spi.service.Bumblebee
```

通过SPI获取的方式

```java
ExtensionLoader<Robot> extensionLoader =
    ExtensionLoader.getExtensionLoader(Robot.class);
Robot optimusPrime = extensionLoader.getExtension("optimusPrime");
optimusPrime.sayHello();
Robot bumblebee = extensionLoader.getExtension("bumblebee");
bumblebee.sayHello();
```

这里可以看到，Dubbo的SPI要比Java SPI更灵活，需要哪个取哪个。



### 服务的暴露过程

#### 服务暴露的全流程

Dubbo的服务暴露过程按照代码流程来分可以分为三个步骤

检查配置  --->  暴露服务（包括了本地暴露和远程暴露）------> 注册服务到注册中心

![img](https://img-blog.csdnimg.cn/img_convert/f57f321c54ce70a27c1d70f9d2383856.png)

从对象构建转换的角度看可以分为两个步骤

服务实现类转成Invoker ----> 将Invoker通过具体协议转换成Exporter

![img](https://img-blog.csdnimg.cn/img_convert/e1e3093e576f62a3c2a0bb318b5b5372.png)

#### 服务暴露源码分析 

`ServiceBean`服务暴露的入口类，该类实现了`ApplicationListener`,监听了spring Ioc容器的刷新事件，当spring容器刷新的的时候会发布一个启动完成的事件，该代码就会执行

```java
public void onApplicationEvent(ContextRefreshedEvent event) {
    if (!isExported() && !isUnexported()) {
        // 注释掉了日志代码
        //服务暴露方法，这里实际上调用的是父类ServiceConfig的export方法
        export();
    }
}
```

ServiceConfig的export

```java
    public synchronized void export() {
        checkAndUpdateSubConfigs();
        if (!shouldExport()) {
            return;
        }
        //延迟暴露 这里就是个ScheduledExecutorService线程池，延迟时间通过`dubbo.provider.delay来配置`
        if (shouldDelay()) {
            DELAY_EXPORT_EXECUTOR.schedule(this::doExport, getDelay(), TimeUnit.MILLISECONDS);
        } else {
            //直接暴露
            doExport();
        }
    }

protected synchronized void doExport() {
    //..... 一些参数校验 注释
    doExportUrls();
}
```

直接暴露方法代码

```java
private void doExportUrls() {
    //获取当前服务的注册中心，可以有多个注册中心，
    List<URL> registryURLs = loadRegistries(true);
    //如果配置了多个协议依次进行暴露
    for (ProtocolConfig protocolConfig : protocols) {
        String pathKey = URL.buildKey(getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), group, version);
        ProviderModel providerModel = new ProviderModel(pathKey, ref, interfaceClass);
        ApplicationModel.initProviderModel(pathKey, providerModel);
        doExportUrlsFor1Protocol(protocolConfig, registryURLs);
    }
}
```

registryURLs里面的元素最终会拼接成下面这个地址

```java
registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-provider&dubbo=2.0.2&pid=18024&qos.enable=false&registry=zookeeper&release=2.7.3&timestamp=1602499480505

//pathKey的值
cn.isuyu.dubbo.demo.common.service.SayService:1.0
```

doExportUrlsFor1Protocol

```java
private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {
    String name = protocolConfig.getName();
    if (StringUtils.isEmpty(name)) {
        name = DUBBO;
    }

    Map<String, String> map = new HashMap<String, String>();
    map.put(SIDE_KEY, PROVIDER_SIDE);

    appendRuntimeParameters(map);
    appendParameters(map, metrics);
    appendParameters(map, application);
    appendParameters(map, module);
    // remove 'default.' prefix for configs from ProviderConfig
    // appendParameters(map, provider, Constants.DEFAULT_KEY);
    appendParameters(map, provider);
    appendParameters(map, protocolConfig);
    appendParameters(map, this);
    if (CollectionUtils.isNotEmpty(methods)) {
    //....  省略的部分就是根据配置往map里面塞值   
    // export service
    String host = this.findConfigedHosts(protocolConfig, registryURLs, map);
    Integer port = this.findConfigedPorts(protocolConfig, name, map);
    URL url = new URL(name, host, port, getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), map);

    if (ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
        .hasExtension(url.getProtocol())) {
        url = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
            .getExtension(url.getProtocol()).getConfigurator(url).configure(url);
    }
```

最终得到的URL就是这个样子，可以看到走的是dubbo协议
![在这里插入图片描述](https://img-blog.csdnimg.cn/2020101514060271.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM4MDgyMzA0,size_16,color_FFFFFF,t_70#pic_center)


根据得到的URL来进行服务暴露

```java
    //上面截图我们可以看到没有scope的key（因为我们配置里面没配）,scope的值会为null
    String scope = url.getParameter(SCOPE_KEY);
    // SCOPE_NONE的值为none  所以这里的代码会进去
    if (!SCOPE_NONE.equalsIgnoreCase(scope)) {
        // 这个SCOPE_REMOTE的值为remote  也会进去
        if (!SCOPE_REMOTE.equalsIgnoreCase(scope)) {
            //服务本地暴露
            exportLocal(url);
        }
        // 如果scope值配置为local，那么只进行本地暴露
        if (!SCOPE_LOCAL.equalsIgnoreCase(scope)) {
            //注册中心不为空 就进行远程服务暴露
            if (CollectionUtils.isNotEmpty(registryURLs)) {
                for (URL registryURL : registryURLs) {
                    //if protocol is only injvm ,not register
                    if (LOCAL_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
                        continue;
                    }
                    //
                    url = url.addParameterIfAbsent(DYNAMIC_KEY, registryURL.getParameter(DYNAMIC_KEY));
                    //如果存在监控中心，添加后会向监控中心汇报
                    URL monitorUrl = loadMonitor(registryURL);
                    if (monitorUrl != null) {
                        url = url.addParameterAndEncoded(MONITOR_KEY, monitorUrl.toFullString());
                    }
                    // For providers, this is used to enable custom proxy to generate invoker
                    String proxy = url.getParameter(PROXY_KEY);
                    if (StringUtils.isNotEmpty(proxy)) {
                        registryURL = registryURL.addParameter(PROXY_KEY, proxy);
                    }
					//具体实现类转换为Invoker对象
                    Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
                    //对invoker进行包装
                    DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
					//invoker转成Exporter
                    Exporter<?> exporter = protocol.export(wrapperInvoker);
                    exporters.add(exporter);
                }
            } else {
                //这里跟if里面执行一样的操作，只不过这里是直接进行暴露
                Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, url);
                DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);

                Exporter<?> exporter = protocol.export(wrapperInvoker);
                exporters.add(exporter);
            }
            /**
                 * @since 2.7.0
                 * ServiceData Store
                 */
            MetadataReportService metadataReportService = null;
            if ((metadataReportService = getMetadataReportService()) != null) {
                metadataReportService.publishProvider(url);
            }
        }
    }
    this.urls.add(url);
```

本地暴露，执行该方法之前，url的协议是dubbo，host和port都是我们配置文件里面配置的，执行完这些代码以后，我们可以看到url协议变成了injvm，host变成了`127.0.0.1`，本地暴露存在的意义，同一个jvm的服务调用避免了网络间的通信开销

```java
private void exportLocal(URL url) {
    URL local = URLBuilder.from(url)
        .setProtocol(LOCAL_PROTOCOL)
        .setHost(LOCALHOST_VALUE)
        .setPort(0)
        .build();
    //这个export就是一个自适应扩展了，方法体为 
    //@Adaptive
    //<T> Exporter<T> export(Invoker<T> invoker) throws RpcException;
    //会在org.apache.dubbo.rpc包下生成的代理类为Protocol$Adaptive类，我把动态生成的类具体代码拷贝到了demo的项目中，该类通过URL参数得到具体的协议，然后通过SPI机制去选择对应的实现类进行export,本地暴露实际调用的就是InjvmProtocol#export方法    
    Exporter<?> exporter = protocol.export(
        PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local));
    exporters.add(exporter);
    logger.info("Export dubbo service " + interfaceClass.getName() + " to local registry url : " + local);
}

```
![在这里插入图片描述](https://img-blog.csdnimg.cn/20201015135544275.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM4MDgyMzA0,size_16,color_FFFFFF,t_70#pic_center)


InjvmProtocol#export

```java
public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
    return new InjvmExporter<T>(invoker, invoker.getUrl().getServiceKey(), exporterMap);
}
```

生成的Export的内容，从图中可以看到实际上就是具体实现类层层封装，invoker是由javassist创建的
![在这里插入图片描述](https://img-blog.csdnimg.cn/20201015135641400.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM4MDgyMzA0,size_16,color_FFFFFF,t_70#pic_center)


为了屏蔽调用细节，统一暴露出一个可执行体，所以封装成了一个invoker，目的是为了调用者能够更简单的使用它，向它发起一个invoker调用，有可能是一个本地的实现，也有可能是一个远程的实现，还可能是一个集群实现

#### 本地暴露流程图

![img](https://img-blog.csdnimg.cn/img_convert/85a87e7264f1d8a6f0aeea51a8bc12da.png)

#### 本地暴露的时序图

#### ![img](https://img-blog.csdnimg.cn/img_convert/081fce94b1261514f3b9ede27e4d3bf2.png)

#### 远程暴露

```java
//注册中心不为空 就进行远程服务暴露
if (CollectionUtils.isNotEmpty(registryURLs)) {
    for (URL registryURL : registryURLs) {
        //if protocol is only injvm ,not register
        if (LOCAL_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
            continue;
        }
        url = url.addParameterIfAbsent(DYNAMIC_KEY, registryURL.getParameter(DYNAMIC_KEY));
        //如果存在监控中心，添加后会向监控中心汇报
        URL monitorUrl = loadMonitor(registryURL);
        if (monitorUrl != null) {
            url = url.addParameterAndEncoded(MONITOR_KEY, monitorUrl.toFullString());
        }
        // 这个proxy依旧为null
        String proxy = url.getParameter(PROXY_KEY);
        if (StringUtils.isNotEmpty(proxy)) {
            registryURL = registryURL.addParameter(PROXY_KEY, proxy);
        }
		//远程暴露封装invoker
        Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
        //对invoker进行包装
        DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
		//invoker转成Exporter
        Exporter<?> exporter = protocol.export(wrapperInvoker);
        exporters.add(exporter);
    }
} else {
    Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, url);
    DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);

    Exporter<?> exporter = protocol.export(wrapperInvoker);
    exporters.add(exporter);
}
```

registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString())后registryURL的值

> registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-provider&dubbo=2.0.2&export=dubbo://10.1.180.116:20880/cn.isuyu.dubbo.demo.common.service.SayService?anyhost=true&application=dubbo-provider&bean.name=ServiceBean:cn.isuyu.dubbo.demo.common.service.SayService:1.0  ...... 后面的省略了

这里会走registry协议，参数中又有export=dubbo:// ,也会走dubbo协议，所以这里会走RegistryProtocol 进行export,这个方法还会根据export字段得到dubbo值，然后执行DubboProtocol 的 export 方法。

 

RegistryProtocol#export 

```java
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        //得到注册中心的URL，eg: zookeeper://127.0.0.1:2181/ 这里具体的值看下面的截图
        URL registryUrl = getRegistryUrl(originInvoker);
        //服务提供者的URL，具体指看下面的截图
        URL providerUrl = getProviderUrl(originInvoker);
        //获取zk订阅服务提供者的URL
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(providerUrl);
        //
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

        providerUrl = overrideUrlWithConfig(providerUrl, overrideSubscribeListener);
        //这个主要是根据URL上的dubbo协议暴露出exporter
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);
        // url to registry
        final Registry registry = getRegistry(originInvoker);
        final URL registeredProviderUrl = getRegisteredProviderUrl(providerUrl, registryUrl);
        ProviderInvokerWrapper<T> providerInvokerWrapper = ProviderConsumerRegTable.registerProvider(originInvoker,
                registryUrl, registeredProviderUrl);
        //to judge if we need to delay publish
        boolean register = registeredProviderUrl.getParameter("register", true);
        //如果需要注册
        if (register) {
            //向注册中心注册，这里就是向zk注册了
            register(registryUrl, registeredProviderUrl);
            providerInvokerWrapper.setReg(true);
        }
        //向注册中心订阅
        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);
        exporter.setRegisterUrl(registeredProviderUrl);
        exporter.setSubscribeUrl(overrideSubscribeUrl);
        //Ensure that a new exporter instance is returned every time export
        return new DestroyableExporter<>(exporter);
    }
```



> registryUrl(注册中心URL)
> ![在这里插入图片描述](https://img-blog.csdnimg.cn/20201015135730982.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM4MDgyMzA0,size_16,color_FFFFFF,t_70#pic_center)


这个方法就是将上面的export=dubbo://... 先转换成exporter,然后获取注册中心的相关配置，如果需要注册则想注册中心注册，并且向ProviderConsumerRegTable这个表格中记录了服务提供者，具体值看下图。
![在这里插入图片描述](https://img-blog.csdnimg.cn/20201015135835783.png#pic_center)


下面看DubboProtocol#export

```java
public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
    //获取URL，dubbo://10.1.180.116:20880/cn.isuyu.dubbo.demo.common.service.TestService
    URL url = invoker.getUrl();
    // key = cn.isuyu.dubbo.demo.common.service.TestService:2.0:20880
    String key = serviceKey(url);
    DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);
    exporterMap.put(key, exporter);
    //export an stub service for dispatching event
    Boolean isStubSupportEvent = url.getParameter(STUB_EVENT_KEY, DEFAULT_STUB_EVENT);
    Boolean isCallbackservice = url.getParameter(IS_CALLBACK_SERVICE, false);
    if (isStubSupportEvent && !isCallbackservice) {
        String stubServiceMethods = url.getParameter(STUB_EVENT_METHODS_KEY);
        if (stubServiceMethods == null || stubServiceMethods.length() == 0) {
            // 日志打印 忽略

        } else {
            stubServiceMethodsMap.put(url.getServiceKey(), stubServiceMethods);
        }
    }
    //打开server
    openServer(url);
    optimizeSerialization(url);
    return exporter;
}

private void openServer(URL url) {
    // 获取ip  10.1.180.116:20880.
    String key = url.getAddress();
    //client can export a service which's only for server to invoke
    boolean isServer = url.getParameter(IS_SERVER_KEY, true);
    if (isServer) {
        //查看缓存中是否已经存在该服务
        ExchangeServer server = serverMap.get(key);
        if (server == null) {
            synchronized (this) {
                server = serverMap.get(key);
                if (server == null) {
                    //创建server
                    serverMap.put(key, createServer(url));
                }
            }
        } else {
            // 缓存中存在 就重置一下
            server.reset(url);
        }
    }
}

private ExchangeServer createServer(URL url) {
	// ......
    ExchangeServer server;
    try {
        //根据URL调用对应的Server,默认是Netty,并初始化Handler
        server = Exchangers.bind(url, requestHandler);
    } catch (RemotingException e) {
        throw new RpcException("Fail to start server(url: " + url + ") " + e.getMessage(), e);
    }
    //.......
    return server;
}
```

我们看下zookeeper的目录有哪些变化，我们只需要关注dubbo就好



#### 完整的dubbo服务暴露的流程图

![img](https://img-blog.csdnimg.cn/img_convert/fdd915bd345aff449cc0c2ba15a20ab1.png)

#### 官网的时序图

![img](https://img-blog.csdnimg.cn/img_convert/1a68758630c28f7d97c7e3eb5dab967d.png)

#### 总结

总的而言服务暴露的过程起始于 Spring IOC 容器刷新完成之时，具体的流程就是根据配置得到 URL，再利用 Dubbo SPI 机制根据 URL 的参数选择对应的实现类，实现扩展。

通过 javassist 动态封装 ref (你写的服务实现类)，统一暴露出 Invoker 使得调用方便，屏蔽底层实现细节，然后封装成 exporter 存储起来，等待消费者的调用，并且会将 URL 注册到注册中心，使得消费者可以获取服务提供者的信息。