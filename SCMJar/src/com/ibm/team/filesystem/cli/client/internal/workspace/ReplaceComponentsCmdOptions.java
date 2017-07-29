package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;











public class ReplaceComponentsCmdOptions
  implements IOptionSource
{
  public ReplaceComponentsCmdOptions() {}
  
  static final PositionalOptionDefinition OPT_TARGET_WORKSAPCE = new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@");
  static final PositionalOptionDefinition OPT_REPLACE_TYPE = new PositionalOptionDefinition("replace-type", 1, 1);
  static final PositionalOptionDefinition OPT_REPLACE_ITEM = new PositionalOptionDefinition("replace", 1, 1, "@");
  
  static final PositionalOptionDefinition OPT_COMPONENTS_SELECTOR = new PositionalOptionDefinition("components", 0, -1);
  static final NamedOptionDefinition OPT_BASELINE = new NamedOptionDefinition("b", "baseline", 1);
  static final NamedOptionDefinition OPT_SOURCE = new NamedOptionDefinition(null, "source", 0, 1, null);
  
  static final NamedOptionDefinition OPT_HIERARCHY = new NamedOptionDefinition("h", "hierarchy", 0);
  static final NamedOptionDefinition OPT_NO_BACKUP = new NamedOptionDefinition("n", "nobackup", 0);
  static final NamedOptionDefinition OPT_ALLOW_MULTIPLE_HIERARCHY = new NamedOptionDefinition("m", "multiple-hierarchy", 0);
  static final NamedOptionDefinition OPT_SKIP_INACCESSIBLE_COMPONENTS = new NamedOptionDefinition("s", "skip-inaccessible", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_ALL, Messages.ReplaceComponentsCmdOptions_ALL_COMPONENTS_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.ReplaceComponentsCmdOptions_FORCE_OVERWRITE_UNCOMMITTED)
      .addOption(OPT_BASELINE, Messages.ReplaceComponentsCmdOptions_BASELINE_HELP)
      .addOption(OPT_SOURCE, Messages.ReplaceComponentsCmdOption_SOURCE_HELP)
      .addOption(OPT_HIERARCHY, Messages.ReplaceComponentsCmdOptions_BASELINE_HIERARCHY_HELP)
      .addOption(OPT_ALLOW_MULTIPLE_HIERARCHY, Messages.ReplaceComponentsCmdOptions_MULTI_HELP)
      .addOption(OPT_SKIP_INACCESSIBLE_COMPONENTS, Messages.ReplaceComponentsCmdOptions_INACCESSIBLE_HELP)
      .addOption(OPT_NO_BACKUP, Messages.ReplaceComponentsCmdOptions_NOBACKUP_HELP)
      .addOption(OPT_TARGET_WORKSAPCE, Messages.ReplaceComponentsCmdOptions_TARGET_WORKSPACE)
      .addOption(OPT_REPLACE_TYPE, NLS.bind(Messages.ReplaceComponentsCmdOptions_REPLACE_TYPE, new String[] { ReplaceComponentsCmd.ReplaceType.NAMES[0], ReplaceComponentsCmd.ReplaceType.NAMES[1], ReplaceComponentsCmd.ReplaceType.NAMES[2] }))
      .addOption(OPT_REPLACE_ITEM, NLS.bind(Messages.ReplaceComponentsCmdOptions_REPLACE_ITEM, OPT_REPLACE_TYPE.getName()))
      .addOption(OPT_COMPONENTS_SELECTOR, Messages.ReplaceComponentsCmdOptions_COMPONENTS_TO_REPLACE);
    

    return options;
  }
}
