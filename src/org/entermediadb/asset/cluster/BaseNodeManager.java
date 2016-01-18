package org.entermediadb.asset.cluster;

import java.util.List;
import java.util.Set;

import org.dom4j.Element;
import org.openedit.OpenEditException;
import org.openedit.WebServer;
import org.openedit.data.SearcherManager;
import org.openedit.node.Node;
import org.openedit.node.NodeManager;
import org.openedit.page.Page;
import org.openedit.page.manage.PageManager;
import org.openedit.util.XmlUtil;

public class BaseNodeManager implements NodeManager
{
	protected Node fieldLocalNode;
	protected XmlUtil fieldXmlUtil;
	protected PageManager fieldPageManager;
	protected SearcherManager fieldSearcherManager;
	protected WebServer fieldWebServer;
	protected Set fieldConnectedCatalogIds;
	
	public Set getConnectedCatalogIds()
	{
		return fieldConnectedCatalogIds;
	}

	public void setConnectedCatalogIds(Set inConnectedCatalogIds)
	{
		fieldConnectedCatalogIds = inConnectedCatalogIds;
	}

	public WebServer getWebServer()
	{
		return fieldWebServer;
	}

	public void setWebServer(WebServer inWebServer)
	{
		fieldWebServer = inWebServer;
	}

	public SearcherManager getSearcherManager()
	{
		return fieldSearcherManager;
	}

	public void setSearcherManager(SearcherManager inSearcherManager)
	{
		fieldSearcherManager = inSearcherManager;
	}

	public PageManager getPageManager()
	{
		return fieldPageManager;
	}

	public void setPageManager(PageManager inPageManager)
	{
		fieldPageManager = inPageManager;
	}

	public XmlUtil getXmlUtil()
	{
		return fieldXmlUtil;
	}

	public void setXmlUtil(XmlUtil inXmlUtil)
	{
		fieldXmlUtil = inXmlUtil;
	}

	public Node getLocalNode()
	{
		if (fieldLocalNode == null)
		{
			Page page = getPageManager().getPage("/WEB-INF/node.xml");
			if( !page.exists())
			{
				throw new OpenEditException("WEB-INF/node.xml is not defined");
			}
			Element root = getXmlUtil().getXml(page.getInputStream(),"UTF-8");
			
			fieldLocalNode = new Node(root);
			String nodeid = getWebServer().getNodeId();
			if( nodeid != null)
			{
				fieldLocalNode.setId( nodeid );
			}
			
		}

		return fieldLocalNode;
	}
	public String getLocalNodeId()
	{
		return getLocalNode().getId();
	}
	public String createDailySnapShot(String inCatalogId)
	{		
		throw new OpenEditException("Not implemented");
	}
	
	public String createSnapShot(String inCatalogId)
	{		
		throw new OpenEditException("Not implemented");
	}
	
	public List listSnapShots(String inCatalogId)
	{
		throw new OpenEditException("Not implemented");
	}
	public void restoreSnapShot(String inCatalogId, String inSnapShotId)
	{
		throw new OpenEditException("Not implemented");
	}

	@Override
	public boolean connectCatalog(String inCatalogId)
	{
		// TODO Auto-generated method stub
		return false;
	}	
}