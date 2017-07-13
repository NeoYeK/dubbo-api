package com.gtp.dubbo.api.core;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.gtp.dubbo.api.annotation.ApiMethod;
import com.gtp.dubbo.api.annotation.ApiService;
import com.gtp.dubbo.api.common.AppConfig;
import com.gtp.dubbo.api.listener.MyListener;
import com.gtp.dubbo.api.metadata.ApiApplicationInfo;
import com.gtp.dubbo.api.metadata.ApiMethodInfo;
import com.gtp.dubbo.api.params.ParameterBinder;
import com.gtp.dubbo.api.utils.ClassLoadUtils;

/**
 * 读取配置文件获得jar 解析jar
 * 
 * @author gaotingping@cyberzone.cn
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ApiInit {

	private static final Logger logger = LoggerFactory.getLogger(MyListener.class);

	public static List<String> getJarPaths() {
		return null;
	}

	public static void parseJar(ParameterBinder parameterBinder, AppConfig appConfig) throws Exception {
		String jarPath = appConfig.getJarPath();
		File dir = new File(jarPath);
		if (!dir.exists()) {
			logger.error("Jar path is not exits:" + jarPath);
			return;
		}

		String[] list = dir.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".jar");
			}
		});
		
		if (list == null || list.length < 1) {
			logger.error("Jar path is empty:" + jarPath);
			return;
		}
		
		for(String path:list){
			parseJar(parameterBinder, jarPath+path);
		}

	}

	public static void parseJar(ParameterBinder parameterBinder, String jarPath) throws Exception {

		/**
		 * 服务注册规划： 1.直接去zk注册中心中找(省事情,但是实现复杂，并且还需要jar信息，可能需要改装dubbo)
		 * 2.简单点的是把jar打包给我，放到指定位置，在管理界面动态注册 register需要的消息 1.application应用名
		 * 2.zk注册中心，多个用“,”隔开
		 * 3.jar名称(都放jar里面/META-INF/publish.properties,总线有个默认的)
		 * 4.负责人信息，以及更详细的dubbo属性等
		 */
		logger.info("===开始注册dubbo服务===");
		logger.info("jarPath="+jarPath);

		ApiApplicationInfo app = ClassLoadUtils.load(jarPath);

		/**
		 * 1.每个应用一个 2.每个jar一个服务 3.zk等地址，放到配置文件
		 */
		// 当前应用配置
		ApplicationConfig application = new ApplicationConfig();
		application.setName(app.getName());

		// 连接注册中心配置
		RegistryConfig registry = new RegistryConfig();
		registry.setProtocol(app.getProtocol());
		registry.setAddress(app.getAddress());

		/**
		 * 注意：ReferenceConfig为重对象，内部封装了与注册中心的连接 以及与服务提供方的连接引用远程服务,此实例很重，封装了与注册中
		 * 心的连接以及与提供者的连接，请自行缓存，否则可能造成内存和连接 泄漏,每个接口一个对象reference对象
		 */
		// 每个服务一个reference
		List<Class<?>> list = app.getServices();
		if (list == null || list.isEmpty()) {
			return;
		}

		Map<String, Map<String, ApiMethodInfo>> services = new HashMap<>();

		for (Class<?> c : list) {

			ReferenceConfig reference = new ReferenceConfig();
			reference.setApplication(application);
			reference.setRegistry(
					registry); /* 多个注册中心可以用setRegistries(),其内部也是这样转换的 */
			reference.setInterface(c);
			reference.setCheck(false);/* #bug fix 不检测服务提供者，防止个别服务错误导致api启动不了 */

			// reference.setVersion("1.0.0");//超时 版本 重试次数
			// reference.setTimeout(6000);
			// reference.setRetries(0);

			// 注意：此代理对象内部封装了所有通讯细节，对象较重，请缓存复用
			Object instance = reference.get();

			// 解析这个类IService，获得所有方法
			ApiService apiService = c.getAnnotation(ApiService.class);

			Map<String, ApiMethodInfo> methods = new HashMap<>();

			for (Method method : c
					.getDeclaredMethods()) {/* DeclaredMethod所有的，不含继承,包括私有的 */
				ApiMethod serviceCode = method.getAnnotation(ApiMethod.class);
				if (serviceCode != null) {
					ApiMethodInfo m = new ApiMethodInfo();
					m.setInstance(instance);
					m.setMethod(method);
					m.setMethodCode(serviceCode.value());
					m.setMethodDesc(serviceCode.desc());
					m.setInParams(parameterBinder.getInParams(method));
					m.setOutParams(parameterBinder.getOutParams(method));
					methods.put(serviceCode.value(), m);
				}
			}

			services.put(apiService.value(), methods);
		}

		ApiManager.register(app.getName(), services);

		logger.info("===结束注册dubbo服务===");
	}
}