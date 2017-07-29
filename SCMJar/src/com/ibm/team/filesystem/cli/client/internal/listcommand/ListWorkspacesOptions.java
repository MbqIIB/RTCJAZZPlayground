package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;








public class ListWorkspacesOptions
  extends ListCmdContribOptions
{
  public static final IOptionKey OPT_NAME_FILTER = new OptionKey("name");
  
  public ListWorkspacesOptions() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = super.getOptions();
    
    options.addOption(OPT_NAME_FILTER, "n", "name", Messages.ListCmdOptions_NAME_FILTER, 1)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP)
      .addOption(new SimpleGroup(false).addOption(ListStreamsOptions.OPT_VISIBILITY, Messages.ListCmdOptions_VISIBILITY, false)
      .addOption(ListStreamsOptions.OPT_PROCESSAREA, NLS.bind(Messages.ListCmdOptions_PROCESSAREA, ListStreamsOptions.OPT_VISIBILITY.getName()), false)
      .addOption(ListStreamsOptions.OPT_ACCESSGROUP, NLS.bind(Messages.ListCmdOptions_ACCESSGROUP, ListStreamsOptions.OPT_VISIBILITY.getName()), false));
    
    return options;
  }
}
