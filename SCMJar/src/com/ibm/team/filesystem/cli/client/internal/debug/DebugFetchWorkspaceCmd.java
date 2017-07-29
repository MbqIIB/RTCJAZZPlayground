package com.ibm.team.filesystem.cli.client.internal.debug;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.util.ArrayList;
import java.util.List;








public class DebugFetchWorkspaceCmd
  extends DebugFetchCmd
{
  private static final String TYPE_WORKSPACE = "com.ibm.team.scm.Workspace";
  
  public DebugFetchWorkspaceCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = super.getOptions();
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
    if (cli.hasOption(OPTION_HISTORY))
      query.add("history=true");
    return query;
  }
  
  protected String getType()
  {
    return "com.ibm.team.scm.Workspace";
  }
}
