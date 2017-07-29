package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;









public class ConflictsCmdOpts
  implements IOptionSource
{
  public static final NamedOptionDefinition OPT_CONFLICTS_MINE = new NamedOptionDefinition("m", "mine", 1);
  

  public static final NamedOptionDefinition OPT_CONFLICTS_PROPOSED = new NamedOptionDefinition("p", "proposed", 1);
  

  public static final NamedOptionDefinition OPT_CONFLICTS_ANCESTOR = new NamedOptionDefinition("a", "ancestor", 1);
  

  public static final NamedOptionDefinition OPT_CONFLICTS_EXTERNAL_COMPARE = new NamedOptionDefinition("x", "xcompare", 1);
  

  public static final NamedOptionDefinition OPT_CONTENT = new NamedOptionDefinition(null, "content", 0);
  

  public static final NamedOptionDefinition OPT_ALL_PROPERTIES = new NamedOptionDefinition(null, "all-properties", 0);
  

  public static final NamedOptionDefinition OPT_PROPERTY_NAMES = new NamedOptionDefinition(null, "property-names", 0);
  

  public static final NamedOptionDefinition OPT_PROPERTY_NAME_VALUE = new NamedOptionDefinition(null, "property-name", 1);
  

  public static final NamedOptionDefinition OPT_LOCAL = new NamedOptionDefinition("l", "local", 0);
  
  public ConflictsCmdOpts() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    options.setLongHelp(NLS.bind(Messages.ConflictsCmdOpts_0, new String[] { OPT_CONFLICTS_MINE.getName(), 
      OPT_CONFLICTS_PROPOSED.getName(), OPT_CONFLICTS_EXTERNAL_COMPARE.getName() }));
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP)
      .addOption(OPT_LOCAL, Messages.ConflictsCmdOpts_OPT_LOCAL, true)
      .addOption(new ContinuousGroup()
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_CONFLICTS_MINE, Messages.ConflictsCmdOpts_1, false)
      .addOption(OPT_CONFLICTS_PROPOSED, Messages.ConflictsCmdOpts_2, false)
      .addOption(OPT_CONFLICTS_ANCESTOR, Messages.ConflictsCmdOptions_ANCESTOR_HELP, false))
      .addOption(OPT_CONTENT, Messages.ConflictsCmdOptions_CONTENT_HELP, false)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_ALL_PROPERTIES, Messages.ConflictsCmdOptions_ALL_PROPERTIES_HELP, false)
      .addOption(OPT_PROPERTY_NAMES, Messages.ConflictsCmdOptions_PROPERTY_NAMES_HELP, false)
      .addOption(OPT_PROPERTY_NAME_VALUE, Messages.ConflictsCmdOptions_PROPERTY_NAME_VALUE_HELP, false))
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT, false)
      .addOption(CommonOptions.OPT_QUIET, CommonOptions.OPT_QUIET_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_CONFLICTS_EXTERNAL_COMPARE, NLS.bind(Messages.LogoutCmd_CONCATENATE, 
      Messages.ResolveCmdOpts_OPT_TO_COMPARE, Messages.DiffCmdOpts_EXTERNAL_COMPARE), false));
    
    return options;
  }
}
