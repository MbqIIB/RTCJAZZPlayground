package com.ibm.team.filesystem.cli.client.internal.subcommands;

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















public class AcceptCmdOptions
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_INPLACE_CONFLICT_HANDLER = new NamedOptionDefinition("i", "in-place-markers", 0);
  
  public static final NamedOptionDefinition OPT_MODE_BASELINES = new NamedOptionDefinition("b", "baseline", 0);
  
  public static final NamedOptionDefinition OPT_MODE_COMPONENTS = new NamedOptionDefinition("C", "components", 0);
  
  public static final NamedOptionDefinition OPT_MODE_CHANGESETS = new NamedOptionDefinition("c", "changes", 0);
  
  public static final NamedOptionDefinition OPT_MODE_WORKITEMS = new NamedOptionDefinition("W", "workitems", 0);
  
  public static final PositionalOptionDefinition OPT_SELECTORS = new PositionalOptionDefinition("selectors", 0, -1, "@");
  
  public static final NamedOptionDefinition OPT_FLOW_COMPONENTS = new NamedOptionDefinition(null, "flow-components", 0);
  
  public static final NamedOptionDefinition OPT_DISABLE_AUTOMERGE = new NamedOptionDefinition(null, "no-merge", 0);
  
  public static final NamedOptionDefinition OPT_ACCEPT_MISSING_CHANGESETS = new NamedOptionDefinition(null, "accept-missing-changesets", 0);
  
  public static final NamedOptionDefinition OPT_ACCEPT_WITH_PORTING = new NamedOptionDefinition("q", "queue-merges", 0);
  
  public static final NamedOptionDefinition OPT_NWAY_CONFLICT = new NamedOptionDefinition(null, "n-way", 0);
  
  public AcceptCmdOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    options.setLongHelp(NLS.bind(Messages.AcceptCmd2Options_LONG_HELP, new String[] { OPT_MODE_COMPONENTS.getName(), OPT_MODE_BASELINES.getName() }));
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP)
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      
      .addOption(OPT_INPLACE_CONFLICT_HANDLER, 
      Messages.AcceptCmdOptions_1)
      
      .addOption(OPT_DISABLE_AUTOMERGE, Messages.AcceptCmdOptions_DISABLE_AUTOMERGE_HELP)
      .addOption(OPT_ACCEPT_MISSING_CHANGESETS, Messages.AcceptCmdOptions_ACCEPT_MISSING_CHANGESETS)
      
      .addOption(OPT_FLOW_COMPONENTS, Messages.AcceptCmd2Options_OPT_FLOW_COMPONENTS_HELP)
      .addOption(CommonOptions.OPT_COMPONENT_HIERARCHY, Messages.AcceptCmdOptions_COMPONENT_HIERARCHY)
      .addOption(CommonOptions.OPT_MULTIPLE_PARTICIPANTS, Messages.AcceptCmdOptions_ACCEPT_MULTIPLE_PARTICIPANTS)
      .addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.AcceptCmdOptions_FORCE_OVERWRITE_UNCOMMITTED)
      .addOption(OPT_ACCEPT_WITH_PORTING, Messages.AcceptCmdOptions_Accept_With_Porting_Help)
      .addOption(OPT_NWAY_CONFLICT, Messages.AcceptCmdOptions_0)
      .addOption(OPT_SELECTORS, Messages.AcceptCmd2Options_OPT_SELECTOR_HELP)
      .addOption(new ContinuousGroup()
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_MODE_CHANGESETS, Messages.AcceptCmdOptions_OPT_CHANGESETS_HELP, false)
      .addOption(OPT_MODE_BASELINES, Messages.AcceptCmdOptions_OPT_BASELINE_HELP, false)
      .addOption(OPT_MODE_COMPONENTS, Messages.AcceptCmdOptions_OPT_COMPONENTS_HELP, false))
      .addOption(CommonOptions.OPT_STREAM_SOURCE_SELECTOR, "s", "source", 
      Messages.AcceptCmdOptions_OPT_SOURCE_WORKSPACE, 1, false)
      .addOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR, "t", "target", 
      Messages.AcceptCmdOptions_3, 1, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_MODE_WORKITEMS, NLS.bind(Messages.AcceptCmdOptions_OPT_WORKITEMS_HELP, 
      OPT_MODE_CHANGESETS.getName()), false)
      .addOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR, "t", "target", 
      Messages.AcceptCmdOptions_3, 1, false));
    
    return options;
  }
}
