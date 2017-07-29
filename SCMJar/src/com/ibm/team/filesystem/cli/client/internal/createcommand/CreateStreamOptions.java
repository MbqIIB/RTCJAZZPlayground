package com.ibm.team.filesystem.cli.client.internal.createcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;












public class CreateStreamOptions
  extends CreateWorkspaceBaseOptions
{
  public static final IOptionKey OPT_OWNER = new OptionKey("owner", "@");
  public static final IOptionKey OPT_PROJECTAREA = new OptionKey("projectarea");
  public static final IOptionKey OPT_TEAMAREA = new OptionKey("teamarea");
  public static final IOptionKey OPT_AUTOFILELOCKPATTERNS = new OptionKey("auto-lock-files");
  
  public CreateStreamOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    PositionalOptionDefinition OPT_NAME_DEFN = new PositionalOptionDefinition(OPT_NAME, "name", 
      1, 1, "@");
    PositionalOptionDefinition OPT_OWNER_DEFN = new PositionalOptionDefinition(OPT_OWNER, 
      "owner", 0, 1, "@");
    OPT_OWNER_DEFN.setShowAsRequired();
    
    options
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT, false)
      .addOption(OPT_DESC, Messages.CreateWorkspaceCmd_11, false)
      .addOption(super.getMutuallyExclusiveOptions())
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_PROJECTAREA, null, "projectarea", Messages.CreateStreamOptions_PROJECTAREA, 0, true)
      .addOption(OPT_TEAMAREA, null, "teamarea", Messages.CreateStreamOptions_TEAMAREA, 0, true))
      .addOption(OPT_NAME_DEFN, getNameHelp(), true)
      .addOption(OPT_OWNER_DEFN, NLS.bind(Messages.CreateStreamOptions_OWNER, 
      ((OptionKey)OPT_PROJECTAREA).getName(), ((OptionKey)OPT_TEAMAREA).getName()), true)
      .addOption(new NamedOptionDefinition(OPT_AUTOFILELOCKPATTERNS, null, "auto-lock-files", 1, "@"), Messages.CreateStreamCmdOptions_AUTO_FILE_LOCK_PATTERNS, true))
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT, false)
      .addOption(new NamedOptionDefinition(OPT_DUPLICATE, null, "duplicate", 1, "@"), getDuplicateHelp(), true)
      .addOption(OPT_NAME_DEFN, getNameHelp(), true));
    
    return options;
  }
  
  public String getNameHelp()
  {
    return Messages.CreateStreamCmdOptions_NAME;
  }
  
  public String getFlowTargetHelp()
  {
    return Messages.CreateStreamCmdOptions_FLOW_TARGET;
  }
  
  public String getSnapshotHelp()
  {
    return Messages.CreateStreamCmdOptions_SNAPSHOT;
  }
  
  public String getDuplicateHelp()
  {
    return Messages.CreateStreamCmdOptions_Duplicate;
  }
}
