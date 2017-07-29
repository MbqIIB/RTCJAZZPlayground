package com.ibm.team.filesystem.cli.client.internal.querycommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.querycommand.parser.QueryBuilder;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.LogFactory;
import com.ibm.team.scm.common.IGenericQueryNode;
import org.apache.commons.logging.Log;
import org.eclipse.osgi.util.NLS;


















public class ObjectQueryCmd
{
  public ObjectQueryCmd() {}
  
  public static IGenericQueryNode getQuery(String cliQuery, ITeamRepository repo)
    throws CLIFileSystemClientException
  {
    IGenericQueryNode query = QueryBuilder.createScmQuery(cliQuery, repo);
    
    if (query == null)
    {
      throw StatusHelper.inappropriateArgument(NLS.bind(Messages.QueryVersionableCmd_PARSER_ERROR, cliQuery));
    }
    
    return query;
  }
  





  public static String logAndCreateExceptionMessage(String queryString, Exception e)
  {
    String newLine = System.getProperty("line.separator");
    Log log = LogFactory.getLog(QueryCmd.class.getName());
    log.error(e);
    String msg = e.getMessage();
    if (msg == null) {
      msg = NLS.bind(Messages.QueryVersionableCmd_QUERY_FAILED, queryString) + newLine + Messages.ERROR_CHECK_LOG;
    } else {
      msg = msg + newLine + NLS.bind(Messages.QueryVersionableCmd_QUERY_FAILED, queryString) + newLine + Messages.ERROR_CHECK_LOG;
    }
    return msg;
  }
}
