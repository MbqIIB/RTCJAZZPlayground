package com.ibm.team.filesystem.cli.client.internal.debug;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.util.ArrayList;
import java.util.List;









public class DebugFetchComponentCmd
  extends DebugFetchCmd
{
  private static final String TYPE_COMPONENT = "com.ibm.team.scm.Component";
  private static final PositionalOptionDefinition OPTION_WORKSPACE_ITEM_ID = new PositionalOptionDefinition("workspaceItemId", 1, 1);
  private static final String ARG_WORKSPACE_ITEM_ID = "workspaceItemId=";
  
  public DebugFetchComponentCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPTION_ITEM_ID, Messages.DebugFetchCmd_HELP_ITEM_ARG);
    options.addOption(OPTION_WORKSPACE_ITEM_ID, Messages.DebugFetchCmd_HELP_WORKSPACE_ITEM_ID_ARG);
    options.addOption(OPTION_OUTPUT, Messages.DebugFetchCmd_HELP_OUTPUT_ARG);
    options.addOption(OPTION_STATE_ID, Messages.DebugFetchCmd_HELP_STATE_ARG);
    options.addOption(OPTION_CONFIG, "c", "config", Messages.DebugFetchCmd_HELP_CONFIG_ARG, 0);
    options.addOption(OPTION_DEEP, "d", "deep", Messages.DebugFetchCmd_HELP_DEEP_ARG, 0);
    options.addOption(OPTION_HISTORY, "h", "history", Messages.DebugFetchCmd_HELP_HISTORY_ARG, 0);
    return options;
  }
  
  protected List<String> getQueryArgs(ICommandLine cli)
  {
    List<String> query = new ArrayList();
    if (cli.hasOption(OPTION_DEEP))
      query.add("deep=true");
    if (cli.hasOption(OPTION_CONFIG))
      query.add("config=true");
    String workspaceItemId = cli.getOption(OPTION_WORKSPACE_ITEM_ID, null);
    if (workspaceItemId != null)
      query.add("workspaceItemId=" + workspaceItemId);
    if (cli.hasOption(OPTION_HISTORY))
      query.add("history=true");
    return query;
  }
  
  protected String getType()
  {
    return "com.ibm.team.scm.Component";
  }
}
