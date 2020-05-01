package com.ffmpeg.myffmpeg.service.impl;


import com.ffmpeg.myffmpeg.commandbuidler.CommandAssembly;
import com.ffmpeg.myffmpeg.commandbuidler.CommandAssemblyImpl;
import com.ffmpeg.myffmpeg.commandbuidler.CommandBuidler;
import com.ffmpeg.myffmpeg.data.CommandTasker;
import com.ffmpeg.myffmpeg.data.TaskDao;
import com.ffmpeg.myffmpeg.data.TaskDaoImpl;
import com.ffmpeg.myffmpeg.handler.*;
import com.ffmpeg.myffmpeg.service.CommandManagerI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * FFmpeg命令操作管理器
 * 
 * @author eguid
 * @since jdk1.7
 * @version 2017年10月13日
 */
@PropertySource({"classpath:ffmpeg.properties"})
public class CommandManagerIImpl implements CommandManagerI {
	/**
	 * 任务持久化器
	 */
	private TaskDao taskDao = null;
	/**
	 * 任务执行处理器
	 */
	private TaskHandler taskHandler = null;
	/**
	 * 命令组装器
	 */
	private CommandAssembly commandAssembly = null;
	/**
	 * 任务消息处理器
	 */
	private OutHandlerMethod ohm = null;

	/**
	 * 保活处理器
	 */
	private KeepAliveHandler keepAliveHandler=null;




	/**
	 * 全部默认初始化，自动查找配置文件
	 */
	public CommandManagerIImpl() {
		this(null);
	}

	/**
	 * 指定任务池大小的初始化，其他使用默认
	 * @param size
	 */
	public CommandManagerIImpl(Integer size) {
		init(size);
	}
	
	/**
	 * 初始化
	 * @param taskDao
	 * @param taskHandler
	 * @param commandAssembly
	 * @param ohm
	 * @param size
	 */
	public CommandManagerIImpl(TaskDao taskDao, TaskHandler taskHandler, CommandAssembly commandAssembly, OutHandlerMethod ohm, Integer size) {
		super();
		this.taskDao = taskDao;
		this.taskHandler = taskHandler;
		this.commandAssembly = commandAssembly;
		this.ohm = ohm;
		init(size);
	}

	/**
	 * 初始化，如果几个处理器未注入，则使用默认处理器
	 * 
	 * @param size
	 */
	public void init(Integer size) {
		boolean iskeepalive=false;
		if (size == null) {
			size = config.getSize() == null ? 10 : config.getSize();
			iskeepalive=config.isKeepalive();
		}
		
		if (this.ohm == null) {
			this.ohm = new DefaultOutHandlerMethod();
		}
		
		if (this.taskDao == null) {
			this.taskDao = new TaskDaoImpl(size);
			//初始化保活线程
			if(iskeepalive) {
				keepAliveHandler = new KeepAliveHandler(taskDao);
				keepAliveHandler.start();
			}
		}
		
		if (this.taskHandler == null) {
			this.taskHandler = new TaskHandlerImpl(this.ohm);
		}
		
		if (this.commandAssembly == null) {
			this.commandAssembly = new CommandAssemblyImpl();
		}
		
	}

	public void setTaskDao(TaskDao taskDao) {
		this.taskDao = taskDao;
	}

	public void setTaskHandler(TaskHandler taskHandler) {
		this.taskHandler = taskHandler;
	}

	public void setCommandAssembly(CommandAssembly commandAssembly) {
		this.commandAssembly = commandAssembly;
	}

	public void setOhm(OutHandlerMethod ohm) {
		this.ohm = ohm;
	}

	/**
	 * 是否已经初始化
	 * 
	 * @param 如果未初始化时是否初始化
	 * @return
	 */
	public boolean isInit(boolean b) {
		boolean ret = this.ohm == null || this.taskDao == null || this.taskHandler == null|| this.commandAssembly == null;
		if (ret && b) {
			init(null);
		}
		return ret;
	}

	@Override
	public String start(String id, String command) {
		return start(id, command, false);
	}

	@Override
	public String start(String id, String command, boolean hasPath) {
		if (isInit(true)) {
			System.err.println("执行失败，未进行初始化或初始化失败！");
			return null;
		}
		if (id != null && command != null) {
			CommandTasker tasker = taskHandler.process(id, hasPath ? command : config.getPath() + command);
			if (tasker != null) {
				int ret = taskDao.add(tasker);
				if (ret > 0) {
					return tasker.getId();
				} else {
					// 持久化信息失败，停止处理
					taskHandler.stop(tasker.getProcess(), tasker.getThread());
					if (config.isDebug())
						System.err.println("持久化失败，停止任务！");
				}
			}
		}
		return null;
	}
	public CommandTasker starts(String id, String command, boolean hasPath) {
		if (isInit(true)) {
			System.err.println("执行失败，未进行初始化或初始化失败！");
			return null;
		}
		if (id != null && command != null) {
			CommandTasker tasker = taskHandler.process(id, hasPath ? command : config.getPath() + command);
			if (tasker != null) {
				int ret = taskDao.add(tasker);
				if (ret > 0) {
					return tasker;
				} else {
					// 持久化信息失败，停止处理
					taskHandler.stop(tasker.getProcess(), tasker.getThread());
					if (config.isDebug())
						System.err.println("持久化失败，停止任务！");
				}
			}
		}
		return null;
	}

	@Override
	public String start(Map<String, String> assembly) {

		// 参数是否符合要求
		if (assembly == null || assembly.isEmpty() || !assembly.containsKey("appName")) {
			System.err.println("参数不正确，无法执行");
			return null;
		}
		String appName = (String) assembly.get("appName");
		if (appName != null && "".equals(appName.trim())) {
			System.err.println("appName不能为空");
			return null;
		}
		assembly.put("ffmpegPath", config.getPath() + "ffmpeg");
		String command = commandAssembly.assembly(assembly);
		if (command != null) {
			return start(appName, command, true);
		}

		return null;
	}

	@Override
	public String start(String id, CommandBuidler commandBuidler) {

		String command =commandBuidler.get();
		if (command != null) {
			return start(id, command, true);
		}
		return null;
	}

	@Override
	public boolean stop(String id) {
		if (id != null && taskDao.isHave(id)) {
			if (config.isDebug())
				System.out.println("正在停止任务：" + id);
			CommandTasker tasker = taskDao.get(id);
			if (taskHandler.stop(tasker.getProcess(), tasker.getThread())) {
				taskDao.remove(id);
				return true;
			}
		}
		System.err.println("停止任务失败！id=" + id);
		return false;
	}

	@Override
	public int stopAll() {
		Collection<CommandTasker> list = taskDao.getAll();
		Iterator<CommandTasker> iter = list.iterator();
		CommandTasker tasker = null;
		int index = 0;
		while (iter.hasNext()) {
			tasker = iter.next();
			if (taskHandler.stop(tasker.getProcess(), tasker.getThread())) {
				taskDao.remove(tasker.getId());
				index++;
			}
		}
		if (config.isDebug())
			System.out.println("停止了" + index + "个任务！");
		return index;
	}

	@Override
	public CommandTasker query(String id) {
		return taskDao.get(id);
	}

	@Override
	public Collection<CommandTasker> queryAll() {
		return taskDao.getAll();
	}

	@Override
	public void destory() {
		if(keepAliveHandler!=null) {
			//安全停止保活线程
			keepAliveHandler.interrupt();
		}
	}
}
