package com.ibm.team.filesystem.cli.client.internal.debug;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.util.List;








public class DebugFetchItemCmd
  extends DebugFetchCmd
{
  public static final PositionalOptionDefinition OPTION_ITEM_TYPE = new PositionalOptionDefinition("itemType", 1, 1);
  private String type;
  
  public DebugFetchItemCmd() {}
  
  public Options getOptions() throws ConflictingOptionException
  {
    Options options = new Options(false);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPTION_ITEM_TYPE, Messages.DebugFetchCmd_HELP_TYPE_ARG);
    options.addOption(OPTION_ITEM_ID, Messages.DebugFetchCmd_HELP_ITEM_ARG);
    options.addOption(OPTION_OUTPUT, Messages.DebugFetchCmd_HELP_OUTPUT_ARG);
    options.addOption(OPTION_STATE_ID, Messages.DebugFetchCmd_HELP_STATE_ARG);
    return options;
  }
  
  public void run() throws FileSystemException
  {
    type = config.getSubcommandCommandLine().getOption(OPTION_ITEM_TYPE);
    super.run();
  }
  
  protected String getType()
  {
    return type;
  }
  

  protected List<String> getQueryArgs(ICommandLine cli)
  {
    return null;
  }
}
