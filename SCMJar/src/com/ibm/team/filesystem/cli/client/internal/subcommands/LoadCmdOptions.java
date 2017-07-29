package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;










public class LoadCmdOptions
  implements IOptionSource
{
  public static final String READ_STDIN = "-";
  
  public LoadCmdOptions() {}
  
  public static final IOptionKey OPT_FORCE = new OptionKey("force");
  public static final IOptionKey OPT_WORKSPACE_SELECTOR = new OptionKey("ws-selector", "@");
  public static final IOptionKey OPT_REMOTE_LOADRULE_PATH = new OptionKey("remoteRules");
  public static final IOptionKey OPT_LOCAL_LOADRULE_PATH = new OptionKey("localRules");
  
  public static final IOptionKey OPT_LOAD_TARGET = new OptionKey("filesystem-target");
  
  public static final IOptionKey OPT_LOAD_COMPONENT_ROOTS = new OptionKey("load-component-root");
  
  public static final IOptionKey OPT_REMOTE_PATHS = new OptionKey("remote-path");
  public static final IOptionKey OPT_ALTERNATIVE_NAME = new OptionKey("alternative-name");
  public static final IOptionKey OPT_RESYNC = new OptionKey("resync");
  
  public static final NamedOptionDefinition OPT_DIR = new NamedOptionDefinition(null, "dir", 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.LoadCmdOptions_0);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, "d", "directory", Messages.LoadCmdOptions_2, 1)
      .addOption(OPT_DIR, Messages.LoadCmdOptions_2)
      .addOption(CommonOptions.OPT_QUIET, CommonOptions.OPT_QUIET_HELP)
      .addOption(CommonOptions.OPT_ALL, Messages.LoadCmdOptions_5)
      .addOption(OPT_FORCE, "f", "force", Messages.LoadCmdOptions_4, 0)
      .addOption(OPT_RESYNC, "s", "resync", Messages.LoadCmdOptions_8, 0)
      .addOption(OPT_LOAD_COMPONENT_ROOTS, "i", "include-root", Messages.LoadCmdOptions_LOAD_COMPONENT_ROOTS_HELP, 0)
      .addOption(OPT_REMOTE_LOADRULE_PATH, "R", "remote-rules", Messages.LoadCmdOptions_6, 1)
      .addOption(OPT_LOCAL_LOADRULE_PATH, "L", "local-rules", NLS.bind(Messages.LoadCmdOptions_7, ((OptionKey)OPT_REMOTE_PATHS).getName()), 1);
    
    options
      .addOption(OPT_LOAD_TARGET, "t", "target", Messages.LoadCmdOptions_LOAD_TARGET_HELP, 1)
      .addOption(OPT_ALTERNATIVE_NAME, "a", "alternative-name", Messages.LoadCmdOptions_ALTERNATIVE_NAME_HELP, 1);
    
    options
      .addOption(new PositionalOptionDefinition(OPT_WORKSPACE_SELECTOR, "workspace", 1, 1, "@"), Messages.LoadCmdOptions_1)
      .addOption(new PositionalOptionDefinition(OPT_REMOTE_PATHS, "remote-path", 0, -1), Messages.LoadCmdOptions_REMOTE_PATHS_HELP);
    
    OPT_DIR.hideOption();
    return options;
  }
}
