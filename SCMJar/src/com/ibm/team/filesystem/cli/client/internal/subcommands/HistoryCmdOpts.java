package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;










public class HistoryCmdOpts
  implements IOptionSource
{
  public HistoryCmdOpts() {}
  
  public static final PositionalOptionDefinition OPT_FILE = new PositionalOptionDefinition("selector", 0, 1);
  

  public static final NamedOptionDefinition OPT_COMPONENT_DEPRECATED = new NamedOptionDefinition("c", "component_deprecated", 1, "@");
  

  public static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("C", "component", 1, "@");
  

  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", "workspace", 1, "@");
  
  public static final NamedOptionDefinition OPT_REMOTE_PATH = new NamedOptionDefinition("R", "remotePath", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    OPT_COMPONENT_DEPRECATED.hideOption();
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, Messages.HistoryCmdOpts_7)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP)
      .addOption(new SimpleGroup(false)
      .addOption(OPT_REMOTE_PATH, Messages.LockAcquireCmd_OPT_REMOTEPATH_HELP, true)
      .addOption(OPT_WORKSPACE, Messages.HistoryCmdOpts_WORKSPACE_HELP, true)
      .addOption(OPT_COMPONENT_DEPRECATED, Messages.HistoryCmdOpts_5, true)
      .addOption(OPT_COMPONENT, Messages.HistoryCmdOpts_5, true))
      .addOption(OPT_FILE, NLS.bind(Messages.HistoryCmdOpts_6, OPT_REMOTE_PATH.getName()));
    
    return options;
  }
}
