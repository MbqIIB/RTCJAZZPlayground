package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;








public class ListComponentsOptions
  implements IOptionSource
{
  public ListComponentsOptions() {}
  
  public static final PositionalOptionDefinition OPT_SELECTOR = new PositionalOptionDefinition("selector", 0, 1, "@");
  public static final IOptionKey OPT_PROJECTAREA = new OptionKey("projectarea");
  public static final IOptionKey OPT_TEAMAREA = new OptionKey("teamarea");
  public static final IOptionKey OPT_NAME_FILTER = new OptionKey("name");
  public static final IOptionKey OPT_CONTRIB = new OptionKey("contrib");
  
  public static final NamedOptionDefinition OPT_VISIBILITY = new NamedOptionDefinition(null, "visibility", 1);
  public static final NamedOptionDefinition OPT_PROCESSAREA = new NamedOptionDefinition(null, "process-area", 1);
  public static final NamedOptionDefinition OPT_ACCESSGROUP = new NamedOptionDefinition(null, "accessgroup", 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, Messages.ListComponentsOptions_1)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP, false)
      .addOption(CommonOptions.OPT_ALL, Messages.ListComponentsAll, false))
      .addOption(new MutuallyExclusiveGroup()
      .addOption(new NamedOptionDefinition(CommonOptions.OPT_ISSNAPSHOT, "s", "snapshot", 0), Messages.ListComponentsOptions_4, false)
      .addOption(OPT_NAME_FILTER, "n", "name", Messages.ListComponentsOptions_NAME_FILTER, 0, false))
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_PROJECTAREA, null, "projectarea", Messages.ListCmdOptions_PROJECTAREA, 1, false)
      .addOption(OPT_TEAMAREA, null, "teamarea", Messages.ListCmdOptions_TEAMAREA, 1, false)
      .addOption(OPT_CONTRIB, "c", "contrib", Messages.ListCmdOptions_0, 1, false))
      .addOption(new SimpleGroup(false).addOption(OPT_VISIBILITY, Messages.ListCmdOptions_VISIBILITY, false)
      .addOption(OPT_PROCESSAREA, NLS.bind(Messages.ListCmdOptions_PROCESSAREA, OPT_VISIBILITY.getName()), false)
      .addOption(OPT_ACCESSGROUP, NLS.bind(Messages.ListCmdOptions_ACCESSGROUP, OPT_VISIBILITY.getName()), false))
      .addOption(OPT_SELECTOR, NLS.bind(Messages.ListComponentsOptions_0, 
      ((OptionKey)CommonOptions.OPT_ISSNAPSHOT).getName(), ((OptionKey)OPT_NAME_FILTER).getName()));
    
    return options;
  }
}
