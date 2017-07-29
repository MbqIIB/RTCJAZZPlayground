package com.ibm.team.filesystem.cli.client.internal.flowcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;

















public class DeliverCmdOptions
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_MODE_COMPONENTS = new NamedOptionDefinition("C", "components", 0);
  
  public static final NamedOptionDefinition OPT_MODE_CHANGESETS = new NamedOptionDefinition("c", "changes", 0);
  
  public static final NamedOptionDefinition OPT_MODE_BASELINES = new NamedOptionDefinition("b", "baselines", 0);
  
  public static final NamedOptionDefinition OPT_MODE_WORKITEMS = new NamedOptionDefinition("W", "workitems", 0);
  
  public static final PositionalOptionDefinition OPT_SELECTORS = new PositionalOptionDefinition("selectors", 0, -1, "@");
  
  public static final NamedOptionDefinition OPT_HARMONIZE_HISTORY = new NamedOptionDefinition("h", "harmonize-history", 0);
  

  public static final NamedOptionDefinition OPT_OVERWRITE_UNCOMMITTED_DEPRECATED = new NamedOptionDefinition("o", "overwrite-uncommitted", 0);
  

  public static final NamedOptionDefinition OPT_IGNORE_UNCOMMITTED = new NamedOptionDefinition("i", "ignore-uncommitted", 0);
  
  public DeliverCmdOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    options.setLongHelp(NLS.bind(Messages.DeliverCmdOptions_LONG_HELP, new String[] { OPT_MODE_COMPONENTS.toString(), OPT_MODE_CHANGESETS.toString() }));
    
    SubcommandUtil.addRepoLocationToOptions(options);
    OPT_OVERWRITE_UNCOMMITTED_DEPRECATED.hideOption();
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(CommonOptions.OPT_QUIET, CommonOptions.OPT_QUIET_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(OPT_OVERWRITE_UNCOMMITTED_DEPRECATED, 
      Messages.DeliverCmdOptions_COMMIT_ANYWAY_HELP)
      .addOption(OPT_IGNORE_UNCOMMITTED, 
      Messages.DeliverCmdOptions_COMMIT_ANYWAY_HELP)
      .addOption(OPT_HARMONIZE_HISTORY, Messages.DeliverCmdOptions_OPT_HARMONIZE_HISTORY_HELP)
      .addOption(OPT_SELECTORS, Messages.DeliverCmdOptions_OPT_SELECTOR_HELP)
      .addOption(new ContinuousGroup()
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_MODE_CHANGESETS, Messages.DeliverCmdOptions_OPT_CHANGESETS_HELP, false)
      .addOption(OPT_MODE_BASELINES, Messages.DeliverCmdOptions_OPT_BASELINES_HELP, false)
      .addOption(OPT_MODE_COMPONENTS, Messages.DeliverCmdOptions_OPT_COMPONENTS_HELP, false))
      .addOption(CommonOptions.OPT_STREAM_SOURCE_SELECTOR, "s", "source", 
      Messages.DeliverCmdOptions_OPT_SOURCE_WORKSPACE, 1, false)
      .addOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR, "t", "target", 
      Messages.DeliverCmdOptions_OPT_TARGET_WORKSPACE_HELP, 1, false))
      .addOption(CommonOptions.OPT_COMPONENT_HIERARCHY, Messages.DeliverCmdOptions_COMPONENT_HIERARCHY)
      .addOption(CommonOptions.OPT_MULTIPLE_PARTICIPANTS, Messages.DeliverCmdOptions_MULTIPLE_PARTICIPANTS)
      .addOption(new ContinuousGroup()
      .addOption(OPT_MODE_WORKITEMS, Messages.DeliverCmdOptions_OPT_WORKITEMS_HELP, false)
      .addOption(CommonOptions.OPT_STREAM_SOURCE_SELECTOR, "s", "source", 
      Messages.DeliverCmdOptions_OPT_SOURCE_WORKSPACE, 1, false))
      .addOption(CommonOptions.OPT_RELEASE_LOCK, Messages.DeliverCmdOptions_OPT_RELEASE_AUTO_LOCK);
    
    return options;
  }
}
