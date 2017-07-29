package com.ibm.team.filesystem.cli.client.internal.debug;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.util.ArrayList;
import java.util.List;








public class DebugFetchChangeSetCmd
  extends DebugFetchCmd
{
  private static final String TYPE_CHANGESET = "com.ibm.team.scm.ChangeSet";
  
  public DebugFetchChangeSetCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = super.getOptions();
    options.addOption(OPTION_DEEP, "d", "deep", Messages.DebugFetchCmd_HELP_DEEP_ARG, 0);
    return options;
  }
  
  protected List<String> getQueryArgs(ICommandLine cli)
  {
    if (!cli.hasOption(OPTION_DEEP))
      return null;
    List<String> query = new ArrayList();
    query.add("deep=true");
    return query;
  }
  
  protected String getType()
  {
    return "com.ibm.team.scm.ChangeSet";
  }
}
