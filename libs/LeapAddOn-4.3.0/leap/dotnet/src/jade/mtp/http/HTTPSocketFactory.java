/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A.

The updating of this file to JADE 2.0 has been partially supported by the
IST-1999-10211 LEAP Project

This file refers to parts of the FIPA 99/00 Agent Message Transport
Implementation Copyright (C) 2000, Laboratoire d'Intelligence
Artificielle, Ecole Polytechnique Federale de Lausanne

GNU Lesser General Public License

This library is free software; you can redistribute it sand/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation,
version 2.1 of the License.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
*****************************************************************/

package jade.mtp.http;

//#PJAVA_EXCLUDE_FILE

import jade.core.Profile;
import jade.mtp.MTPException;

import java.io.IOException;
import java.net.InetAddress;


import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;


/**
 * Singleton class for obtaining sockets. HTTP MTP calls methods use this class
 * every time that a socket is needed. 
 * 
 * @author <a href="mailto:Joan.Ametller@uab.es">Joan Ametller Esquerra</a>
 * 
 */
public class HTTPSocketFactory {

  public static HTTPSocketFactory getInstance() {
    if (_instance == null)
      _instance = new HTTPSocketFactory();
    return _instance;
  }

	public void configure(Profile profile, HTTPAddress hta) throws Exception {

		 _socketFactory = SocketFactory.getDefault();
		 _serverSocketFactory = ServerSocketFactory.getDefault();

		connectTimeout = Integer.parseInt(profile.getParameter(MTP_HTTP_PREFIX + "connectTimeout",
		        Integer.toString(DEFAULT_CONNECT_TIMEOUT)));
	}

  public Socket createSocket(String host, int port) throws IOException {
    Socket s;
    

	 s = _socketFactory.createSocket(host, port);
    
    return s;    
  }

  public Socket createSocket(
    String host,
    int port,
    InetAddress dest,
    int outport)
    throws IOException {
    Socket s;
    

	 s = _socketFactory.createSocket(host, port, dest, outport);
    
    return s;
  }

  public ServerSocket createServerSocket(int port) throws IOException {
    ServerSocket ss = _serverSocketFactory.createServerSocket(port);
    return ss;
  }

  private HTTPSocketFactory() {
  }

  private static HTTPSocketFactory _instance;
  private static final String PREFIX = "jade_mtp_http_https_";
  private static final String MTP_HTTP_PREFIX = "jade_mtp_http_";
  private static final int DEFAULT_CONNECT_TIMEOUT = -1;
  private SocketFactory _socketFactory;
  private ServerSocketFactory _serverSocketFactory;
  private boolean _needClientAuth = false;
  private boolean _usingHttps = false;
  private int connectTimeout;  
}
