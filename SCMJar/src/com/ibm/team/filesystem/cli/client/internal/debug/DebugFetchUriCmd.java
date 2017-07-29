package com.ibm.team.filesystem.cli.client.internal.debug;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;









public class DebugFetchUriCmd
  extends DebugFetchCmd
{
  public static final PositionalOptionDefinition OPTION_ITEM_URI = new PositionalOptionDefinition("uri", 1, 1);
  
  public DebugFetchUriCmd() {}
  
  public Options getOptions() throws ConflictingOptionException
  {
    Options options = new Options(false);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPTION_ITEM_URI, Messages.DebugFetchCmd_HELP_URI_ARG);
    options.addOption(OPTION_OUTPUT, Messages.DebugFetchCmd_HELP_OUTPUT_ARG);
    return options;
  }
  


  protected URI getURI(String repoURI, ICommandLine cli)
    throws TeamRepositoryException
  {
    try
    {
      return new URI(cli.getOption(OPTION_ITEM_URI));
    } catch (URISyntaxException e) {
      throw new TeamRepositoryException(e);
    }
  }
  

  protected String getType()
  {
    return null;
  }
  

  protected List<String> getQueryArgs(ICommandLine cli)
  {
    return null;
  }
}
