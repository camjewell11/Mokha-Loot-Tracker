package com.camjewell;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MokhaLootTracker
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MokhaLostLootTrackerPlugin.class);
		RuneLite.main(args);
	}
}