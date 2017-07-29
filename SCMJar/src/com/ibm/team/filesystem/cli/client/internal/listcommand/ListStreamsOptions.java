package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;









public class ListStreamsOptions
  extends ListCmdOptions
{
  public ListStreamsOptions() {}
  
  public static final IOptionKey OPT_PROJECTAREA = new OptionKey("projectarea");
  public static final IOptionKey OPT_TEAMAREA = new OptionKey("teamarea");
  public static final NamedOptionDefinition OPT_VISIBILITY = new NamedOptionDefinition(null, "visibility", 1);
  public static final NamedOptionDefinition OPT_PROCESSAREA = new NamedOptionDefinition(null, "process-area", 1);
  public static final NamedOptionDefinition OPT_ACCESSGROUP = new NamedOptionDefinition(null, "accessgroup", 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = super.getOptions();
    
    options
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_PROJECTAREA, null, "projectarea", Messages.ListCmdOptions_PROJECTAREA, 1, false)
      .addOption(OPT_TEAMAREA, null, "teamarea", Messages.ListCmdOptions_TEAMAREA, 1, false))
      .addOption(ListWorkspacesOptions.OPT_NAME_FILTER, "n", "name", Messages.ListCmdOptions_NAME_FILTER, 1)
      .addOption(new SimpleGroup(false).addOption(OPT_VISIBILITY, Messages.ListCmdOptions_VISIBILITY, false)
      .addOption(OPT_PROCESSAREA, NLS.bind(Messages.ListCmdOptions_PROCESSAREA, OPT_VISIBILITY.getName()), false)
      .addOption(OPT_ACCESSGROUP, NLS.bind(Messages.ListCmdOptions_ACCESSGROUP, OPT_VISIBILITY.getName()), false))
      
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP);
    
    return options;
  }
}
