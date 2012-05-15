package uk.co.aviva.gibbyj.scapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
//TODO handle where we have less than 4 results (not sure if it works yet)
//TODO sort by test names

public class SCAPIActivity extends Activity {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MblnTimerRunning = false;
		try {
			// User name / password / SC Account / refresh interval are held as
			// preferences
			// we retrieve them here
			SharedPreferences sharedPrefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			MstrUserName = sharedPrefs.getString("UserName", "");
			MstrPassword = sharedPrefs.getString("Password", "");
			MstrAccount = sharedPrefs.getString("Account", "");
			String strInterval = sharedPrefs.getString("Refresh", "60000");
			MintRefresh = Integer.parseInt(strInterval);
			boolean blnFullScreen = sharedPrefs.getBoolean("FullScreen", false);
			MintFontSize = Integer.parseInt(sharedPrefs.getString("FontSize", "10"));
			MblnRestart = false;
			MblnTimerRunning = false;
			// If preferences not set open the screen to input them
			if (MstrUserName.equals("") || MstrPassword.equals("")
					|| MstrAccount.equals("")) {
				MblnRestart = true;
				startActivity(new Intent(this, QuickPrefsActivity.class));
			}

			if (blnFullScreen) {
				requestWindowFeature(Window.FEATURE_NO_TITLE);
				getWindow().setFlags(
						WindowManager.LayoutParams.FLAG_FULLSCREEN,
						WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
			// Browser control to display the HTML generated
			objWebView = new WebView(this);
			setContentView(objWebView);
			// get the security key for this session
			getKey();
			// Number of tests to retrieve
			MintRetTests = 40;
			// if refresh is 0 only do manual refresh
			if (MintRefresh > 0) {
				// start the refresh timer
				objTimer = new Timer();
				objUpdateScreen = new UpdateScreen();
				// start after 1 second then every MintRefresh milliseconds
				// (default 60 seconds)
				objTimer.schedule(objUpdateScreen, 1000, MintRefresh);
				MblnTimerRunning = true;
			} else {
				refreshScreen();
			}
		} catch (Exception e) {
			Log.e("SCAPIActivity", "Exception " + e.getLocalizedMessage());
		}
	}

	// Menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, 0, 0, "Preferences");
		menu.add(Menu.NONE, 1, 0, "Refresh");
		menu.add(Menu.NONE, 2, 0, "Exit");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 0:
			// show preferences menu
			MblnRestart = true;
			startActivity(new Intent(this, QuickPrefsActivity.class));
			return true;
		case 1:
			// refresh screen
			refreshScreen();
			return true;
		case 2:
			// Exit
			finish();
			return true;
		}
		return false;
	}

	// Stop timer if loses focus
	@Override
	protected void onStop() {
		super.onStop();
		try {
			if (MintRefresh > 0) {
				objUpdateScreen.cancel();
				MblnTimerRunning = false;
			}
		} catch (Exception e) {
			Log.e("onStop", "Exception " + e.getLocalizedMessage());
		}
	}

	// restart timer when get focus back
	@Override
	protected void onResume() {
		super.onResume();
		if (MblnRestart) {
			SharedPreferences sharedPrefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			MstrUserName = sharedPrefs.getString("UserName", "");
			MstrPassword = sharedPrefs.getString("Password", "");
			MstrAccount = sharedPrefs.getString("Account", "");
			String strInterval = sharedPrefs.getString("Refresh", "60000");
			MintRefresh = Integer.parseInt(strInterval);
			MintFontSize = Integer.parseInt(sharedPrefs.getString("FontSize", "10"));
			MblnRestart = false;
			// Browser control to display the HTML generated
			objWebView = new WebView(this);
			setContentView(objWebView);
			// get the security key for this session
			getKey();
			// Number of tests to retrieve
			MintRetTests = 40;
		}
		try {
			if (MblnTimerRunning == false && MintRefresh > 0) {
				objTimer.schedule(objUpdateScreen, 1000, MintRefresh);
				MblnTimerRunning = true;
			}
		} catch (Exception e) {
			Log.e("onResume", "Exception " + e.getLocalizedMessage());
		}
	}

	private WebView objWebView;
	private Timer objTimer;
	private UpdateScreen objUpdateScreen;
	private String MstrColourOK = "\"#00CC00\"";
	private String MstrBackColourOK = "\"#FFFFFF\"";
	private String MstrColourWarning = "\"#FFCC33\"";
	private String MstrColourProblem = "\"#FF0000\"";
	private String MstrColourWhite = "White";
	private String MstrColourBlack = "Black";
	private String MstrUserName;
	private String MstrPassword;
	private String MstrAccount;
	private int MintRefresh;
	private String MstrKey;
	private int MintRetTests;
	private boolean MblnTimerRunning;
	private int MintFontSize;
	private Calendar MdtKeyExpire;
	private boolean MblnRestart;

	// timer task to update the screen
	class UpdateScreen extends TimerTask {
		public void run() {
			try {
				String strHtml = null;
				strHtml = buildHtml();
				objWebView.loadData(strHtml, "text/html", null);
			} catch (Exception e) {
				Log.e("UpdateScreen", "Exception " + e.getLocalizedMessage());
			}
		}
	}

	// Manual refresh
	private void refreshScreen() {
		try {
			String strHtml = null;
			strHtml = buildHtml();
			objWebView.loadData(strHtml, "text/html", null);
		} catch (Exception e) {
			Log.e("refreshScreen", "Exception " + e.getLocalizedMessage());
		}
	}

	// Build the HTML from the XML returned from the SC API
	private String buildHtml() throws Exception {
		String strHtml;
		String strXML;
		String strUrl;
		String strEndTime;
		String strStartTime;
		String strDate;
		// set the start and end times for the call
		Calendar dtNow = Calendar.getInstance();
		// if the SC security key has expired get a new one
		if (dtNow.compareTo(MdtKeyExpire) == 1) {
			getKey();
		}
		Calendar dtStart = Calendar.getInstance();
		dtStart.add(Calendar.HOUR, -2);
		dtNow.add(Calendar.HOUR, 1);
		SimpleDateFormat sdfNow = new SimpleDateFormat("HH:mm:ss", Locale.UK);
		SimpleDateFormat sdfNowDt = new SimpleDateFormat("yyyy-MM-dd",
				Locale.UK);
		strEndTime = sdfNow.format(dtNow.getTime());
		strStartTime = sdfNow.format(dtStart.getTime());
		strDate = sdfNowDt.format(dtNow.getTime());

		// Build the URL to call the API with
		strUrl = "https://api.siteconfidence.co.uk/current/"
				+ MstrKey
				+ "/AccountId/"
				+ MstrAccount
				+ "/Return/[Account[Pages[Page[Id,Label,LastTestLocalDateTime,TestResults[Count,TestResult[LocalDateTime,TotalSeconds,Status,Result]]]],UserJourneys[UserJourney[Id,Label,LastTestLocalDateTime,TestResults[Count,TestResult[TestServer,LocalDateTime,Speed,StatusCode,Status,ResultCode,FailedStep]]]]]]/StartDate/"
				+ strDate + "/StartTime/" + strStartTime + "/EndTime/"
				+ strEndTime + "/LimitTestResults/" + MintRetTests + "/";
		// Call the API and store the returned XML
		strXML = executeHttpGet(strUrl);

		// start the build the HTML including the table headings
		strHtml = "<html>";
		strHtml = strHtml + "<head>";
		strHtml = strHtml + "<title>Site Confidence Monitor</title>";
		strHtml = strHtml
				+ "<style type=\"text/css\">BODY {background:#FFFFFF; color:#000000; margin-left:0.0cm; margin-right:0.0cm; margin-top:0.0cm;scrollbar-face-color:#E6E6FA; scrollbar-shadow-color:#9999FF}TH {color:white; font-family:Tahoma; font-size:" + MintFontSize + "pt}TD {font-family:Tahoma; font-size:" + MintFontSize + "pt}.formbar {COLOR:#ffffff; background:#000000}.formtxt {margin-left:10px; padding-top:3px; background:#E6E6FA}</style>";
		strHtml = strHtml + "</head>";
		strHtml = strHtml + "<body>";
		strHtml = strHtml + "<table>";
		strHtml = strHtml + "<tr>";
		strHtml = strHtml + "<th bgcolor=\"#000000\" width=\"60%\">Name</th>";
		strHtml = strHtml
				+ "<th bgcolor=\"#000000\" width=\"10%\">Status -3</th>";
		strHtml = strHtml
				+ "<th bgcolor=\"#000000\" width=\"10%\">Status -2</th>";
		strHtml = strHtml
				+ "<th bgcolor=\"#000000\" width=\"10%\">Status -1</th>";
		strHtml = strHtml + "<th bgcolor=\"#000000\" width=\"10%\">Status</th>";
		strHtml = strHtml + "</tr>";

		Document doc;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		Element pageNode;
		Element objNodeTestResults;
		Element objNodeTestResult;
		NodeList objNodeListTestResult;
		NodeList nodeList;
		NodeList tmpNodeList;
		String strLabel;
		String strStatus;
		String strTemp;
		String strLastTestLocalDateTime;
		String strLocalDateTime;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
				Locale.UK);
		SimpleDateFormat sdf2 = new SimpleDateFormat("HH:mm", Locale.UK);
		Date tmpDate;
		Date tmpDate2;
		Calendar tmpCal2 = Calendar.getInstance();
		int intFirstTest;
		int intLoopTests;
		int intCount;
		int intHours;
		String strCount;

		try {
			// Load the XML string into a dom document
			db = dbf.newDocumentBuilder();
			doc = db.parse(new InputSource(new StringReader(strXML)));
			doc.getDocumentElement().normalize();
			// do the while loop for Pages then for User Journeys
			intLoopTests = 1;
			while (intLoopTests < 3) {
				if (intLoopTests == 1) {
					// gets a list of page nodes
					nodeList = doc.getElementsByTagName("Page");
				} else {
					// gets a list of user journey nodes
					nodeList = doc.getElementsByTagName("UserJourney");
				}
				// loop through for each Node
				for (int i = 0; i < nodeList.getLength(); i++) {
					// get the page/userjourney node
					pageNode = (Element) nodeList.item(i);
					// start the row
					strHtml = strHtml + "<tr class=\"formtxt\">";
					// Get the label for this test
					strLabel = pageNode.getAttribute("Label");
					// Get the test result Node
					tmpNodeList = pageNode.getElementsByTagName("TestResults");
					objNodeTestResults = (Element) tmpNodeList.item(0);
					// Count contains the number of results that could be
					// returned (not the number actually returned)
					// We want all results so we need to increase the results
					// requested next time around if we didn't get them this
					// time
					strCount = objNodeTestResults.getAttribute("Count");
					intCount = Integer.parseInt(strCount);
					if (intCount > MintRetTests) {
						MintRetTests = intCount;
					}
					// Get the time stamp for the latest complete test
					// (we want to ignore tests still running later in the code)
					strLastTestLocalDateTime = pageNode
							.getAttribute("LastTestLocalDateTime");
					tmpDate = sdf.parse(strLastTestLocalDateTime);
					// get a list of test result nodes
					objNodeListTestResult = objNodeTestResults
							.getElementsByTagName("TestResult");

					// holds the index number in the list for the first test we
					// are interested in
					// set to -1 to start with
					intFirstTest = -1;
					// sc does not do BST properly so adjust times
					// LastTestLocalDateTime is correct but LocalDateTime is GMT
					// not BST
					if (objNodeListTestResult.getLength() > 0) {
						// get the time stamp for the last test node (latest)
						objNodeTestResult = (Element) objNodeListTestResult
								.item(objNodeListTestResult.getLength() - 1);
						strLocalDateTime = objNodeTestResult
								.getAttribute("LocalDateTime");
						tmpDate2 = sdf.parse(strLocalDateTime);
						// if the latest test is in the future subtract an hour
						// from the test times
						if (tmpDate2.compareTo(dtNow.getTime()) > 0) {
							intHours = -1;
						} else {
							intHours = 0;
						}
					} else {
						intHours = 0;
					}

					// find the latest completed test
					// start with the most recent and work backwards until we
					// find one
					// less than or equal to LastTestLocalDateTime
					for (int j = objNodeListTestResult.getLength(); j > 0; j--) {
						objNodeTestResult = (Element) objNodeListTestResult
								.item(j - 1);
						strLocalDateTime = objNodeTestResult
								.getAttribute("LocalDateTime");
						tmpDate2 = sdf.parse(strLocalDateTime);
						tmpCal2.setTime(tmpDate2);
						tmpCal2.add(Calendar.HOUR, intHours);
						tmpDate2 = tmpCal2.getTime();
						if (tmpDate2.compareTo(tmpDate) <= 0) {
							intFirstTest = j;
							break;
						}
					}

					// Build html row for this test
					try {
						strTemp = "";
						// if we didn't find any tests use 4 empty cells in the
						// row
						if (intFirstTest == -1) {
							strStatus = "OK";
							strTemp = "<td align=\"center\">.</td><td align=\"center\">.</td><td align=\"center\">.</td><td align=\"center\">.</td>";
						} else {
							// if we have less than 4 tests use empty cells for
							// each test we don't have
							int intDisplayTests = 3;
							if (intFirstTest < 4) {
								intDisplayTests = intFirstTest - 1;
								for (int j = 4; j > intFirstTest; j--) {
									strTemp = strTemp
											+ "<td align=\"center\">.</td>";
								}

							}
							strStatus = "";
							for (int j = intFirstTest - intDisplayTests; j <= intFirstTest; j++) {
								objNodeTestResult = (Element) objNodeListTestResult
										.item(j - 1);
								strStatus = objNodeTestResult
										.getAttribute("Status");
								strLocalDateTime = objNodeTestResult
										.getAttribute("LocalDateTime");
								tmpDate2 = sdf.parse(strLocalDateTime);
								tmpCal2.setTime(tmpDate2);
								tmpCal2.add(Calendar.HOUR, intHours);
								tmpDate2 = tmpCal2.getTime();
								if (strStatus.equals("OK")) {
									strTemp = strTemp
											+ "<td align=\"center\" bgcolor="
											+ MstrColourOK + " style=\"color:"
											+ MstrColourBlack + "\">"
											+ sdf2.format(tmpDate2) + "</td>";
								} else if (strStatus.equals("Warning")) {
									strTemp = strTemp
											+ "<td align=\"center\" bgcolor="
											+ MstrColourWarning
											+ " style=\"color:"
											+ MstrColourBlack + "\">"
											+ sdf2.format(tmpDate2) + "</td>";
								} else {
									strTemp = strTemp
											+ "<td align=\"center\" bgcolor="
											+ MstrColourProblem
											+ " style=\"color:"
											+ MstrColourWhite + "\">"
											+ sdf2.format(tmpDate2) + "</td>";
								}

							}
						}
					} catch (Exception e) {
						Log.e("buildHtml " + strLabel,
								"Exception " + e.getLocalizedMessage());
						strStatus = "OK";
						strTemp = "<td align=\"center\">.</td><td align=\"center\">.</td><td align=\"center\">.</td><td align=\"center\">.</td>";
					}
					// Create the test name with the correct background colour
					if (strStatus.equals("OK")) {
						strHtml = strHtml + "<td bgcolor=" + MstrBackColourOK
								+ " style=\"color:" + MstrColourBlack + "\">";
					} else if (strStatus.equals("Warning")) {
						strHtml = strHtml + "<td bgcolor=" + MstrColourWarning
								+ " style=\"color:" + MstrColourBlack + "\">";
					} else {
						strHtml = strHtml + "<td bgcolor=" + MstrColourProblem
								+ " style=\"color:" + MstrColourWhite + "\">";
					}
					strHtml = strHtml + strLabel + "</td>";
					strHtml = strHtml + strTemp;
					strHtml = strHtml + "</tr>";

				}
				intLoopTests++;
			}
		} catch (ParserConfigurationException e) {
			Log.e("buildHtml",
					"ParserConfigurationException " + e.getLocalizedMessage());
		} catch (SAXException e) {
			Log.e("buildHtml", "SAXException " + e.getLocalizedMessage());
		} catch (IOException e) {
			Log.e("buildHtml", "IOException " + e.getLocalizedMessage());
		} catch (ParseException e) {
			Log.e("buildHtml", "ParseException " + e.getLocalizedMessage());
		} catch (Exception e) {
			Log.e("buildHtml", "Exception " + e.getLocalizedMessage());
		}

		strHtml = strHtml + "</table>";
		strHtml = strHtml + "</body>";
		strHtml = strHtml + "</html>";
		return Uri.encode(strHtml);

	}

	private void getKey() {
		try {
			String strUrl = "https://api.siteconfidence.co.uk/current/username/"
					+ MstrUserName + "/password/" + MstrPassword;
			String strXML = executeHttpGet(strUrl);
			Document doc;
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db;
			db = dbf.newDocumentBuilder();
			doc = db.parse(new InputSource(new StringReader(strXML)));
			NodeList objAPINl = doc.getElementsByTagName("ApiKey");
			// @Lifetime="3600"
			Element objAPINode = (Element) objAPINl.item(0);
			String strLifeTime = objAPINode.getAttribute("Lifetime");
			int intLifeTime = Integer.parseInt(strLifeTime);
			MdtKeyExpire = Calendar.getInstance();
			MdtKeyExpire.add(Calendar.SECOND, intLifeTime);
			StringBuffer buffer = new StringBuffer();
			NodeList childList = objAPINode.getChildNodes();
			for (int i = 0; i < childList.getLength(); i++) {
				Node child = childList.item(i);
				if (child.getNodeType() == Node.TEXT_NODE) {
					buffer.append(child.getNodeValue());
				}
			}

			MstrKey = buffer.toString();

		} catch (ParserConfigurationException e) {
			Log.e("getKey",
					"ParserConfigurationException " + e.getLocalizedMessage());
		} catch (SAXException e) {
			Log.e("getKey", "SAXException " + e.getLocalizedMessage());
		} catch (IOException e) {
			Log.e("getKey", "IOException " + e.getLocalizedMessage());
		} catch (Exception e) {
			Log.e("getKey", "Exception " + e.getLocalizedMessage());
		}
	}

	public String executeHttpGet(String strURL) {
		BufferedReader in = null;
		String page;
		URL url;
		page = "";
		try {
			url = new URL(strURL);
			URI uri;
			uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
					url.getPort(), url.getPath(), url.getQuery(), url.getRef());
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();
			request.setURI(uri);
			HttpResponse response;
			response = client.execute(request);
			in = new BufferedReader(new InputStreamReader(response.getEntity()
					.getContent()));
			StringBuffer sb = new StringBuffer("");
			String line = "";
			String NL = System.getProperty("line.separator");
			while ((line = in.readLine()) != null) {
				sb.append(line + NL);
			}
			in.close();
			page = sb.toString();
		} catch (MalformedURLException e) {
			Log.e("executeHttpGet",
					"MalformedURLException " + e.getLocalizedMessage());
		} catch (ClientProtocolException e) {
			Log.e("executeHttpGet",
					"ClientProtocolException " + e.getLocalizedMessage());
		} catch (IOException e) {
			Log.e("executeHttpGet", "IOException " + e.getLocalizedMessage());
		} catch (URISyntaxException e) {
			Log.e("executeHttpGet",
					"URISyntaxException " + e.getLocalizedMessage());
		} catch (IllegalStateException e) {
			Log.e("executeHttpGet",
					"IllegalStateException " + e.getLocalizedMessage());
		} catch (Exception e) {
			Log.e("executeHttpGet", "Exception " + e.getLocalizedMessage());
		}

		return page;
	}
}