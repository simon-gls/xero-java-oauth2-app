package com.xero.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.xero.api.ApiClient;
import com.xero.api.client.IdentityApi;
import com.xero.models.identity.Connection;

@WebServlet("/Callback")
public class Callback extends HttpServlet {
    private static final long serialVersionUID = 1L;
    final String clientId = System.getenv("XERO_CLIENT_ID");
    final String clientSecret = System.getenv("XERO_CLIENT_SECRET");
    final String redirectURI = System.getenv("XERO_REDIRECT_URI");
    final String TOKEN_SERVER_URL = "https://identity.xero.com/connect/token";
    final String AUTHORIZATION_SERVER_URL = "https://login.xero.com/identity/connect/authorize";
    final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    final JsonFactory JSON_FACTORY = new JacksonFactory();

    /**
     * @see HttpServlet#HttpServlet()
     */
    public Callback() {
        super();
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
     *      response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        System.out.println("Callback invoked");
        System.out.println("clientId=" + clientId);
        System.out.println("clientSecret=" + clientSecret);
        System.out.println("redirectURI=" + redirectURI);

        String code = "123";
        if (request.getParameter("code") != null) {
            code = request.getParameter("code");
        }

        // Retrieve your stored secretState variable 
        TokenStorage store = new TokenStorage();
        String secretState =store.get(request, "state");
 
        // Compare to state prevent CSRF
        if (request.getParameter("state") != null && secretState.equals(request.getParameter("state").toString())) {

            ArrayList<String> scopeList = new ArrayList<String>();
            scopeList.add("openid");
            scopeList.add("email");
            scopeList.add("profile");
            scopeList.add("offline_access");
            scopeList.add("accounting.settings");
            scopeList.add("accounting.transactions");
            scopeList.add("accounting.contacts");
            scopeList.add("accounting.journals.read");
            scopeList.add("accounting.reports.read");
            scopeList.add("accounting.attachments");
            
            DataStoreFactory DATA_STORE_FACTORY = new MemoryDataStoreFactory();

            AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(),
                    HTTP_TRANSPORT, JSON_FACTORY, new GenericUrl(TOKEN_SERVER_URL),
                    new ClientParametersAuthentication(clientId, clientSecret), clientId, AUTHORIZATION_SERVER_URL)
                    .setScopes(scopeList).setDataStoreFactory(DATA_STORE_FACTORY).build();

            TokenResponse tokenResponse = flow.newTokenRequest(code).setRedirectUri(redirectURI).execute();

            ApiClient defaultIdentityClient = new ApiClient("https://api.xero.com", null, null, null, null);
            IdentityApi idApi = new IdentityApi(defaultIdentityClient);
            List<Connection> connection = idApi.getConnections(tokenResponse.getAccessToken(),null);
        
            //TokenStorage store = new TokenStorage();
            store.saveItem(response, "jwt_token", tokenResponse.toPrettyString());
            store.saveItem(response, "id_token", tokenResponse.get("id_token").toString());
            store.saveItem(response, "access_token", tokenResponse.getAccessToken());
            store.saveItem(response, "refresh_token", tokenResponse.getRefreshToken());
            store.saveItem(response, "expires_in_seconds", tokenResponse.getExpiresInSeconds().toString());
            store.saveItem(response, "xero_tenant_id", connection.get(0).getTenantId().toString());

            response.sendRedirect("./AuthenticatedResource");
        } else {
            System.out.println("Invalid state - possible CSFR");
        }

    }
}