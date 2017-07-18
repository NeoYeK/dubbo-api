package com.gtp.dubbo.api.core;

import java.util.List;

/**
 * jar加载,解析与刷新等
 * 
 * @author gaotingping@cyberzone.cn
 */
public interface ApiJarService {

	/**
	 * 重启的时候，全部重新加载
	 */
	public void initAll() throws Exception;

	/**
	 * 刷新单个jar
	 * 重新加载的时候，以文件的md5为区分jar是否改变
	 * 
	 * @param jarPath
	 */
	public void refresh(String jarName) throws Exception;

	/**
	 * 刷新jar工作区(所有jar)，以文件的md5值为区分是否更新
	 */
	public void refreshAll() throws Exception;
	
	/**
	 * 获得所有jar
	 * 
	 * @return
	 */
	public List<String> getAll();
}
