package com.r3.csde;

import kong.unirest.json.JSONArray;
import org.gradle.api.Project;
import net.corda.v5.base.types.MemberX500Name;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import org.jetbrains.annotations.NotNull;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;

public class CsdeRpcInterface {
    private Project project;
    private String baseURL = "https://localhost:8888";
    private String rpcUser = "admin";
    private String rpcPasswd = "admin";
    private String workspaceDir = "./workspace";
    static private int retryWaitMs = 1000;
    static PrintStream out = System.out;
    static private String CPIUploadStatusBaseName = "CPIFileStatus.json";
    static private String CPIUploadStatusFName;
    static private String X500ConfigFile = "config/dev-net.json";
    static private String javaBinDir;
    static private String cordaPidCache = "CordaPIDCache.dat";
    static private String dbContainerName;
    private String JDBCDir;
    private String combinedWorkerBinRe;

    public CsdeRpcInterface() {
    }

    public CsdeRpcInterface (Project inProject,
                             String inBaseUrl,
                             String inRpcUser,
                             String inRpcPasswd,
                             String inWorkspaceDir,
                             String inJavaBinDir,
                             String inDbContainerName,
                             String inJDBCDir,
                             String inCordaPidCache
    ) {
        project = inProject;
        baseURL = inBaseUrl;
        rpcUser = inRpcUser;
        rpcPasswd = inRpcPasswd;
        workspaceDir = inWorkspaceDir;
        javaBinDir = inJavaBinDir;
        cordaPidCache = inCordaPidCache;
        dbContainerName = inDbContainerName;
        JDBCDir = inJDBCDir;
        CPIUploadStatusFName = workspaceDir +"/"+ CPIUploadStatusBaseName;

    }


    static private void rpcWait(int millis) {
        try {
            sleep(millis);
        }
        catch(InterruptedException e) {
            throw new UnsupportedOperationException("Interrupts not supported.", e);
        }
    }

    static private void rpcWait() {
        rpcWait(retryWaitMs);
    }

    public LinkedList<String> getConfigX500Ids() throws IOException {
        LinkedList<String> x500Ids = new LinkedList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        FileInputStream in = new FileInputStream(X500ConfigFile);
        com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(in);
        for( com.fasterxml.jackson.databind.JsonNode identity:  jsonNode.get("identities")) {
            String idAsString = identity.toString();
            x500Ids.add(idAsString.substring(1,idAsString.length()-1));
        }
        return x500Ids;
    }

    static public String getLastCPIUploadChkSum(@NotNull String CPIUploadStatusFName) throws IOException, NullPointerException {

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        FileInputStream in = new FileInputStream(CPIUploadStatusFName);
        com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(in);


        String checksum = jsonNode.get("cpiFileChecksum").toString();
        if(checksum == null || checksum.equals("null")) {
            throw new NullPointerException("Missing cpiFileChecksum in file " + CPIUploadStatusFName+ " with contents:" + jsonNode);
        }
        return checksum;
    }


    public void reportError(@NotNull kong.unirest.HttpResponse<kong.unirest.JsonNode> response) throws CsdeException {

        out.println("*** *** ***");
        out.println("Unexpected response from Corda");
        out.println("Status="+ response.getStatus());
        out.println("*** Headers ***\n"+ response.getHeaders());
        out.println("*** Body ***\n"+ response.getBody());
        out.println("*** *** ***");
        throw new CsdeException("Error: unexpected response from Corda.");
    }

    public void downloadFile(String url, String targetPath) {
        Unirest.get(url)
                .asFile(targetPath)
                .getBody();
    }

    public kong.unirest.HttpResponse<kong.unirest.JsonNode> getVNodeInfo() {
        Unirest.config().verifySsl(false);
        return Unirest.get(baseURL + "/api/v1/virtualnode/")
                .basicAuth(rpcUser, rpcPasswd)
                .asJson();
    }

    public void listVNodesVerbose() {
        kong.unirest.HttpResponse<kong.unirest.JsonNode> vnodeResponse = getVNodeInfo();
        out.println("VNodes:\n" + vnodeResponse.getBody().toPrettyString());
    }

    // X500Name, shorthash,
    public void listVNodes() {
        kong.unirest.HttpResponse<kong.unirest.JsonNode> vnodeResponse = getVNodeInfo();

        kong.unirest.json.JSONArray virtualNodesJson = (JSONArray) vnodeResponse.getBody().getObject().get("virtualNodes");

        List<List<String>> lines = new LinkedList<>();
        for (Object o : virtualNodesJson) {
            if (o instanceof kong.unirest.json.JSONObject) {
                kong.unirest.json.JSONObject idObj = ((kong.unirest.json.JSONObject) o).getJSONObject("holdingIdentity");
                String x500Name = idObj.get("x500Name").toString();
                String shortHash = idObj.get("shortHash").toString();
                lines.add(Arrays.asList(x500Name, shortHash));
            }
        }
        List<String> title = Arrays.asList("X500 Name", "Holding identity short hash");
        List<Integer> titleSizes = Arrays.asList(60, 30);
        printTable(titleSizes, title, lines);
    }

    public void printTable(List<Integer> titleSizes, List<String> title, List<List<String>> lines) {
        int width = titleSizes.stream().reduce(0, Integer::sum);
        String separator = "-".repeat(width + 1);
        out.println(separator);
        out.println(formatLine(titleSizes, title));
        out.println(separator);
        for (List<String> line : lines) {
            out.println(formatLine(titleSizes, line));
        }
        out.println(separator);
    }

    public String formatLine(List<Integer> titleSizes, List<String> line) {
        StringBuilder sb = new StringBuilder();
        int delta = 0;
        for (int i = 0; i < titleSizes.size(); i++) {
            String s = line.get(i);
            sb.append("| ").append(s);
            delta += titleSizes.get(i) - (2 + s.length());

            if (delta > 0) {
                sb.append(" ".repeat(delta));
                delta = 0;
            } else {
                sb.append(" ");
                delta -= 1;
            }
        }
        sb.append("|");
        return sb.toString();
    }

    public kong.unirest.HttpResponse<kong.unirest.JsonNode> getCpiInfo() {
        Unirest.config().verifySsl(false);
        return Unirest.get(baseURL + "/api/v1/cpi/")
                .basicAuth(rpcUser, rpcPasswd)
                .asJson();

    }

    public void listCPIs() {
        kong.unirest.HttpResponse<kong.unirest.JsonNode> cpiResponse  = getCpiInfo();
        kong.unirest.json.JSONArray jArray = (JSONArray) cpiResponse.getBody().getObject().get("cpis");

        for(Object o: jArray){
            if(o instanceof kong.unirest.json.JSONObject) {
                kong.unirest.json.JSONObject idObj = ((kong.unirest.json.JSONObject) o).getJSONObject("id");
                out.print("cpiName=" + idObj.get("cpiName"));
                out.println(", cpiVersion=" + idObj.get("cpiVersion"));
            }
        }
    }

    public void uploadCertificate(String certAlias, String certFName) {
        Unirest.config().verifySsl(false);
        kong.unirest.HttpResponse<kong.unirest.JsonNode> uploadResponse = Unirest.put(baseURL + "/api/v1/certificates/cluster/code-signer/")
                .field("alias", certAlias)
                .field("certificate", new File(certFName))
                .basicAuth(rpcUser, rpcPasswd)
                .asJson();
        out.println("Certificate/key upload, alias "+certAlias+" certificate/key file "+certFName);
        out.println(uploadResponse.getBody().toPrettyString());
    }

    public void forceuploadCPI(String cpiFName) throws FileNotFoundException, CsdeException {
        Unirest.config().verifySsl(false);
        kong.unirest.HttpResponse<kong.unirest.JsonNode> jsonResponse = Unirest.post(baseURL + "/api/v1/maintenance/virtualnode/forcecpiupload/")
                .field("upload", new File(cpiFName))
                .basicAuth(rpcUser, rpcPasswd)
                .asJson();

        if(jsonResponse.getStatus() == 200) {
            String id = (String) jsonResponse.getBody().getObject().get("id");
            out.println("get id:\n" +id);
            kong.unirest.HttpResponse<kong.unirest.JsonNode> statusResponse = uploadStatus(id);

            if (statusResponse.getStatus() == 200) {
                PrintStream cpiUploadStatus = new PrintStream(new FileOutputStream(CPIUploadStatusFName));
                cpiUploadStatus.print(statusResponse.getBody());
                out.println("Caching CPI file upload status:\n" + statusResponse.getBody());
            } else {
                reportError(statusResponse);
            }
        }
        else {
            reportError(jsonResponse);
        }
    }

    private boolean uploadStatusRetry(kong.unirest.HttpResponse<kong.unirest.JsonNode> response) {
        int status = response.getStatus();
        kong.unirest.JsonNode body = response.getBody();
        // Do not retry on success
        if(status == 200) {
            // Keep retrying until we get "OK" may move through "Validateing upload", "Persisting CPI"
            return !(body.getObject().get("status").equals("OK"));
        }
        else if (status == 400){
            JSONObject details = response.getBody().getObject().getJSONObject("details");
            if( details != null ){
                String code = (String) details.getString("code");
                return !code.equals("BAD_REQUEST");
            }
            else {
                // 400 otherwise means some transient problem
                return true;
            }
        }
        return false;
    }

    public kong.unirest.HttpResponse<kong.unirest.JsonNode> uploadStatus(String requestId) {
        kong.unirest.HttpResponse<kong.unirest.JsonNode> statusResponse = null;
        do {
            rpcWait(1000);
            statusResponse = Unirest
                    .get(baseURL + "/api/v1/cpi/status/" + requestId + "/")
                    .basicAuth(rpcUser, rpcPasswd)
                    .asJson();
            out.println("Upload status="+statusResponse.getStatus()+", status query response:\n"+statusResponse.getBody().toPrettyString());
        }
        while(uploadStatusRetry(statusResponse));

        return statusResponse;
    }

    public void deployCPI(String cpiFName, String cpiName, String cpiVersion) throws FileNotFoundException, CsdeException {
        Unirest.config().verifySsl(false);

        kong.unirest.HttpResponse<kong.unirest.JsonNode> cpiResponse  = getCpiInfo();
        kong.unirest.json.JSONArray jArray = (JSONArray) cpiResponse.getBody().getObject().get("cpis");


        int matches = 0;
        for(Object o: jArray.toList() ) {
            if(o instanceof JSONObject) {
                JSONObject idObj = ((JSONObject) o).getJSONObject("id");
                if((idObj.get("cpiName").toString().equals(cpiName)
                        && idObj.get("cpiVersion").toString().equals(cpiVersion))) {
                    matches++;
                }
            }
        }
        out.println("Matching CPIS="+matches);


        if(matches == 0) {
            kong.unirest.HttpResponse<kong.unirest.JsonNode> uploadResponse = Unirest.post(baseURL + "/api/v1/cpi/")
                    .field("upload", new File(cpiFName))
                    .basicAuth(rpcUser, rpcPasswd)
                    .asJson();

            kong.unirest.JsonNode body = uploadResponse.getBody();

            int status = uploadResponse.getStatus();

            out.println("Upload Status:" + status);
            out.println("Pretty print the body\n" + body.toPrettyString());

            // We expect the id field to be a string.
            if (status == 200) {
                String id = (String) body.getObject().get("id");
                out.println("get id:\n" + id);

                kong.unirest.HttpResponse<kong.unirest.JsonNode> statusResponse = uploadStatus(id);
                if (statusResponse.getStatus() == 200) {
                    PrintStream cpiUploadStatus = new PrintStream(new FileOutputStream(CPIUploadStatusFName));
                    cpiUploadStatus.print(statusResponse.getBody());
                    out.println("Caching CPI file upload status:\n" + statusResponse.getBody());
                } else {
                    reportError(statusResponse);
                }
            } else {
                reportError(uploadResponse);
            }
        }
        else {
            out.println("CPI already uploaded doing a 'force' upload.");
            forceuploadCPI(cpiFName);
        }
    }

    public void createAndRegVNodes() throws IOException, CsdeException{
        Unirest.config().verifySsl(false);
        String cpiCheckSum = getLastCPIUploadChkSum( CPIUploadStatusFName );

        LinkedList<String> x500Ids = getConfigX500Ids();
        LinkedList<String> OKHoldingShortIds = new LinkedList<>();

        // For each identity check that it already exists.
        Set<MemberX500Name> existingX500 = new HashSet<>();
        kong.unirest.HttpResponse<kong.unirest.JsonNode> vnodeListResponse = getVNodeInfo();

        kong.unirest.json.JSONArray virtualNodesJson = (JSONArray) vnodeListResponse.getBody().getObject().get("virtualNodes");
        for(Object o: virtualNodesJson){
            if(o instanceof kong.unirest.json.JSONObject) {
                kong.unirest.json.JSONObject idObj = ((kong.unirest.json.JSONObject) o).getJSONObject("holdingIdentity");
                String x500id = (String) idObj.get("x500Name");
                existingX500.add(MemberX500Name.parse( x500id) );
            }
        }

        // Create the VNodes
        for(String x500id: x500Ids) {
            if(!existingX500.contains(MemberX500Name.parse(x500id) )) {
                out.println("Creating VNode for x500id=\"" + x500id + "\" cpi checksum=" + cpiCheckSum);
                kong.unirest.HttpResponse<kong.unirest.JsonNode> jsonNode = Unirest.post(baseURL + "/api/v1/virtualnode")
                        .body("{ \"request\" : { \"cpiFileChecksum\": " + cpiCheckSum + ", \"x500Name\": \"" + x500id + "\" } }")
                        .basicAuth(rpcUser, rpcPasswd)
                        .asJson();
                // Logging.

                // need to check this and report errors.
                // 200 - OK
                // 409 - Vnode already exists
                if (jsonNode.getStatus() != 409) {
                    if (jsonNode.getStatus() != 200) {
                        reportError(jsonNode);
                    } else {
                        JSONObject thing = jsonNode.getBody().getObject().getJSONObject("holdingIdentity");
                        String shortHash = (String) thing.get("shortHash");
                        OKHoldingShortIds.add(shortHash);
                    }
                }
            }
            else {
                out.println("Not creating a vnode for \"" + x500id + "\", vnode already exists.");
            }
        }

        // Register the VNodes
        for(String shortHoldingIdHash: OKHoldingShortIds) {
            kong.unirest.HttpResponse<kong.unirest.JsonNode> vnodeResponse = Unirest.post(baseURL + "/api/v1/membership/" + shortHoldingIdHash)
                    .body("{ \"memberRegistrationRequest\": { \"action\": \"requestJoin\",  \"context\": { \"corda.key.scheme\" : \"CORDA.ECDSA.SECP256R1\" } } }")
                    .basicAuth(rpcUser, rpcPasswd)
                    .asJson();

            out.println("Vnode membership submission:\n" + vnodeResponse.getBody().toPrettyString());
        }

    }

    public void startCorda() throws Exception {
        File cordaPIDFile = new File(cordaPidCache);
        if (cordaPIDFile.exists()) {
            throw new Exception("Cannot start the Combined worker\nCached process ID file " + cordaPIDFile + " existing.\nWas the combined worker already started?");
        }

        PrintStream pidStore = new PrintStream(new FileOutputStream(cordaPIDFile));
        File combinedWorkerJar = project.getConfigurations().getByName("combinedWorker").getSingleFile();

        new ProcessBuilder(
                "docker",
                "run", "-d", "--rm",
                "-p", "5432:5432",
                "--name", dbContainerName,
                "-e", "POSTGRES_DB=cordacluster",
                "-e", "POSTGRES_USER=postgres",
                "-e", "POSTGRES_PASSWORD=password",
                "postgres:latest").start();
        rpcWait(10000);

        ProcessBuilder procBuild = new ProcessBuilder(javaBinDir + "/java",
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
                "-Dco.paralleluniverse.fibers.verifyInstrumentation=true",
                "-jar",
                combinedWorkerJar.toString(),
                "--instanceId=0",
                "-mbus.busType=DATABASE",
                "-spassphrase=password",
                "-ssalt=salt",
                "-spassphrase=password",
                "-ssalt=salt",
                "-ddatabase.user=user",
                "-ddatabase.pass=password",
                "-ddatabase.jdbc.url=jdbc:postgresql://localhost:5432/cordacluster",
                "-ddatabase.jdbc.directory="+JDBCDir);


        procBuild.redirectErrorStream(true);
        Process proc = procBuild.start();
        pidStore.print(proc.pid());
        out.println("Corda Process-id="+proc.pid());
    }

    public void stopCorda() throws IOException, NoPidFile {
        File cordaPIDFile = new File(cordaPidCache);
        if(cordaPIDFile.exists()) {
            Scanner sc = new Scanner(cordaPIDFile);
            long pid = sc.nextLong();
            out.println("pid to kill=" + pid);

            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                new ProcessBuilder("Powershell", "-Command", "Stop-Process", "-Id", Long.toString(pid), "-PassThru").start();
            } else {
                new ProcessBuilder("kill", "-9", Long.toString(pid)).start();
            }

            Process proc = new ProcessBuilder("docker", "stop", dbContainerName).start();

            cordaPIDFile.delete();
        }
        else {
            throw new NoPidFile("Cannot stop the Combined worker\nCached process ID file " + cordaPidCache + " missing.\nWas the combined worker not started?");
        }
    }

}
