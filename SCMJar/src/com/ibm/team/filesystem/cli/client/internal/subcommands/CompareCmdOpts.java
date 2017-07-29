package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;














public class CompareCmdOpts
  implements IOptionSource
{
  static final String SHOW_DIRECTION = "d";
  static final String SHOW_COMPONENT = "c";
  static final String SHOW_BASELINE = "b";
  static final String SHOW_WORKITEM = "w";
  static final String SHOW_OSLCLINK = "o";
  static final String SHOW_CHANGESET = "s";
  static final String SHOW_FILESYSTEM = "f";
  static final String SHOW_ALL = "A";
  static final String DEFAULT_SHOW = "dcbswo";
  public static final String DISPLAY_FIELD_CONTRIB = "c";
  public static final String DISPLAY_FIELD_DATE = "d";
  public static final String DISPLAY_CHANGESET_WORKITEMS = "i";
  public static final String DISPLAY_CHANGESET_OSLC_LINKS = "o";
  public static final String DEFAULT_DISPLAY = "cdo";
  public static final String DIRECTION_INCOMING = "i";
  public static final String DIRECTION_OUTGOING = "o";
  public static final String DIRECTION_BOTH = "b";
  static final String REROOT_ROOT = "r";
  static final String REROOT_DIRECTION = "d";
  static final String REROOT_COMPONENT = "c";
  static final String REROOT_BASELINE = "b";
  static final String PRUNE_DIRECTION = "d";
  static final String PRUNE_COMPONENT = "c";
  
  public CompareCmdOpts() {}
  
  static final PositionalOptionDefinition OPT_COMPARE_TYPE_1 = new PositionalOptionDefinition("new-type", 1, 1);
  static final PositionalOptionDefinition OPT_COMPARE_TYPE_2 = new PositionalOptionDefinition("old-type", 1, 1);
  
  static final NamedOptionDefinition OPT_SHOW = new NamedOptionDefinition("I", "include-types", 1);
  
  static final PositionalOptionDefinition OPT_COMPARE_ITEM_1 = new PositionalOptionDefinition("new", 1, 1, "@");
  static final PositionalOptionDefinition OPT_COMPARE_ITEM_2 = new PositionalOptionDefinition("old", 1, 1, "@");
  
  static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("c", "component", 1);
  
  static final NamedOptionDefinition OPT_FMT_CONTRIB = new NamedOptionDefinition("C", "format-contributor", 1);
  static final NamedOptionDefinition OPT_FMT_DATE = new NamedOptionDefinition("D", "format-date", 1);
  
  static final NamedOptionDefinition OPT_DISPLAY = new NamedOptionDefinition("S", "show", 1);
  
  static final NamedOptionDefinition OPT_DIRECTIONS = new NamedOptionDefinition("f", "flow-directions", 1);
  
  static final NamedOptionDefinition OPT_REROOT = new NamedOptionDefinition("w", "reroot", 1);
  
  static final NamedOptionDefinition OPT_PRUNE = new NamedOptionDefinition("p", "prune", 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT);
    
    options.addOption(OPT_SHOW, NLS.bind(Messages.CompareCmdOpts_FILTER_HELP, new String[] { "d", "c", "b", "w", "s", "f", "o", "dcbswo" }));
    
    options.addOption(OPT_FMT_CONTRIB, Messages.CompareCmdOpts_FORMAT_CONTRIB_HELP);
    options.addOption(OPT_FMT_DATE, Messages.CompareCmdOpts_FORMAT_DATE_HELP);
    
    options.addOption(
      OPT_DISPLAY, 
      NLS.bind(
      Messages.CompareCmdOpts_DISPLAY_HELP, 
      new String[] {
      "c", 
      "d", 
      "i", 
      "o" }));
    

    options.addOption(OPT_DIRECTIONS, NLS.bind(Messages.CompareCmdOpts_OPT_DIRECTION_HELP, new String[] { "i", "o", "b", "b" }));
    
    options.addOption(OPT_REROOT, NLS.bind(Messages.CompareCmdOpts_REROOT_HELP, new String[] { "r", "d", "c", "b" }));
    
    options.addOption(OPT_PRUNE, NLS.bind(Messages.CompareCmdOpts_PRUNE_HELP, "d", "c"));
    
    options.addOption(OPT_COMPONENT, Messages.CompareCmdOpts_COMPONENT_HELP);
    
    options.addOption(OPT_COMPARE_TYPE_1, NLS.bind(Messages.CompareCmdOpts_COMPARE_TYPE_1, new String[] { CompareCmd.CompareType.NAMES[0], CompareCmd.CompareType.NAMES[1], CompareCmd.CompareType.NAMES[2], CompareCmd.CompareType.NAMES[3] }));
    options.addOption(OPT_COMPARE_ITEM_1, NLS.bind(Messages.CompareCmdOpts_COMPARE_ITEM_1, OPT_COMPARE_TYPE_1.getName()));
    
    options.addOption(OPT_COMPARE_TYPE_2, NLS.bind(Messages.CompareCmdOpts_COMPARE_TYPE_2, new String[] { CompareCmd.CompareType.NAMES[0], CompareCmd.CompareType.NAMES[1], CompareCmd.CompareType.NAMES[2], CompareCmd.CompareType.NAMES[3] }));
    options.addOption(OPT_COMPARE_ITEM_2, NLS.bind(Messages.CompareCmdOpts_COMPARE_ITEM_2, OPT_COMPARE_TYPE_1.getName()));
    
    return options;
  }
}
