package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;














public class DiffCmdOpts
  implements IOptionSource
{
  public static final PositionalOptionDefinition OPT_AFTER_TYPE = new PositionalOptionDefinition("after-type", 1, 1);
  

  public static final PositionalOptionDefinition OPT_AFTER_SELECTOR = new PositionalOptionDefinition("after-selector", 1, 1, "@");
  

  public static final PositionalOptionDefinition OPT_BEFORE_TYPE = new PositionalOptionDefinition("before-type", 0, 1);
  

  public static final PositionalOptionDefinition OPT_BEFORE_SELECTOR = new PositionalOptionDefinition("before-selector", 0, 1, "@");
  

  public static final NamedOptionDefinition OPT_FULL_PATCH = new NamedOptionDefinition("p", "fullpatch", 0);
  

  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", "workspace", 1);
  

  public static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("c", "component", 1);
  

  public static final NamedOptionDefinition OPT_DISPLAY = new NamedOptionDefinition("S", "show", 1);
  

  public static final NamedOptionDefinition OPT_EXTERNAL_COMPARE = new NamedOptionDefinition("x", "xcompare", 0);
  

  public static final NamedOptionDefinition OPT_DIFF_FILE_SELECTOR = new NamedOptionDefinition("f", "file", 1, -1, "@");
  public static final String DISPLAY_CONTENT_DIFF = "c";
  public static final String DISPLAY_PROPERTY_DIFF = "p";
  
  public DiffCmdOpts() {}
  
  public Options getOptions() throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    options.setLongHelp(Messages.DiffCmdOpts_1);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(OPT_WORKSPACE, Messages.ChangesetCompleteCmdOptions_0)
      .addOption(OPT_COMPONENT, Messages.DiffCmdOpts_COMPONENT_HELP)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(new SimpleGroup(false)
      .addOption(OPT_EXTERNAL_COMPARE, Messages.DiffCmdOpts_EXTERNAL_COMPARE, false)
      .addOption(OPT_DISPLAY, NLS.bind(Messages.DiffCmdOpts_SHOW_HELP, 
      "c", "p"), false))
      .addOption(OPT_FULL_PATCH, Messages.DiffCmdOpts_7, false))
      .addOption(OPT_AFTER_TYPE, Messages.DiffCmdOpts_0)
      .addOption(OPT_AFTER_SELECTOR, Messages.DiffCmdOpts_8)
      .addOption(OPT_BEFORE_TYPE, Messages.DiffCmdOpts_9)
      .addOption(OPT_BEFORE_SELECTOR, Messages.DiffCmdOpts_10)
      .addOption(CommonOptions.OPT_MAXRESULTS, Messages.DiffCmdOpts_11, false)
      .addOption(OPT_DIFF_FILE_SELECTOR, Messages.DiffCmdOpts_FILE_SELECTOR, false);
    return options;
  }
}
