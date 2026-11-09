import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;

public class Main {
    private static final Path DATA = Paths.get("data");
    private static final Path ASSIGNMENTS = DATA.resolve("assignments.tsv");
    private static final Path SUBMISSIONS = DATA.resolve("submissions.tsv");
    private static final List<Assignment> assignments = new ArrayList<>();
    private static final List<Submission> submissions = new ArrayList<>();
    private static int nextAssignmentId = 1, nextSubmissionId = 1;

    public static void main(String[] args) throws Exception {
        Files.createDirectories(DATA);
        loadData();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/assignments", Main::handleAssignments);
        server.createContext("/api/submissions", Main::handleSubmissions);
        server.createContext("/", Main::serveStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Faculty Assignment Tracker running at http://localhost:8080");
    }

    private static void handleAssignments(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            send(ex, 200, assignmentsJson());
        } else if ("POST".equalsIgnoreCase(method)) {
            Map<String,String> p = params(ex);
            String title = p.getOrDefault("title", "").trim();
            String subject = p.getOrDefault("subject", "").trim();
            String section = p.getOrDefault("section", "").trim();
            String dueDate = p.getOrDefault("dueDate", "").trim();
            String instructions = p.getOrDefault("instructions", "").trim();
            if (title.isEmpty() || subject.isEmpty() || section.isEmpty() || dueDate.isEmpty()) {
                send(ex, 400, "{\"error\":\"Please fill all required fields.\"}"); return;
            }
            Assignment a = new Assignment(nextAssignmentId++, title, subject, section, dueDate, instructions);
            assignments.add(a); saveData();
            send(ex, 201, a.json());
        } else if ("DELETE".equalsIgnoreCase(method)) {
            int id = queryId(ex);
            assignments.removeIf(a -> a.id == id);
            saveData(); send(ex, 200, "{\"message\":\"Assignment deleted\"}");
        } else send(ex, 405, "{\"error\":\"Method not allowed\"}");
    }

    private static void handleSubmissions(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            send(ex, 200, submissionsJson());
        } else if ("POST".equalsIgnoreCase(method)) {
            Map<String,String> p = params(ex);
            String student = p.getOrDefault("student", "").trim();
            String assignment = p.getOrDefault("assignment", "").trim();
            String answer = p.getOrDefault("answer", "").trim();
            if (student.isEmpty() || assignment.isEmpty() || answer.isEmpty()) {
                send(ex, 400, "{\"error\":\"Enter student name, assignment and submission text/link.\"}"); return;
            }
            Submission s = new Submission(nextSubmissionId++, student, assignment, answer, LocalDate.now().toString(), "Pending", "", -1);
            submissions.add(s); saveData(); send(ex, 201, s.json());
        } else if ("PATCH".equalsIgnoreCase(method)) {
            Map<String,String> p = params(ex);
            int id = Integer.parseInt(p.getOrDefault("id", "0"));
            Submission found = submissions.stream().filter(s -> s.id == id).findFirst().orElse(null);
            if (found == null) { send(ex, 404, "{\"error\":\"Submission not found\"}"); return; }
            found.marks = Integer.parseInt(p.getOrDefault("marks", "0"));
            found.feedback = p.getOrDefault("feedback", "");
            found.status = "Reviewed";
            saveData(); send(ex, 200, found.json());
        } else send(ex, 405, "{\"error\":\"Method not allowed\"}");
    }

    private static void serveStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = Paths.get("public").resolve(path.substring(1)).normalize();
        if (!file.startsWith(Paths.get("public")) || !Files.exists(file) || Files.isDirectory(file)) {
            send(ex, 404, "Not found"); return;
        }
        String type = path.endsWith(".css") ? "text/css; charset=utf-8" :
                      path.endsWith(".js") ? "application/javascript; charset=utf-8" : "text/html; charset=utf-8";
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static Map<String,String> params(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String,String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }
    private static int queryId(HttpExchange ex) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return -1;
        for (String part : q.split("&")) if (part.startsWith("id=")) return Integer.parseInt(part.substring(3));
        return -1;
    }
    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,DELETE,PATCH,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
    private static String assignmentsJson() {
        StringJoiner j = new StringJoiner(",", "[", "]");
        for (Assignment a : assignments) j.add(a.json()); return j.toString();
    }
    private static String submissionsJson() {
        StringJoiner j = new StringJoiner(",", "[", "]");
        for (Submission s : submissions) j.add(s.json()); return j.toString();
    }
    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
    private static void loadData() throws IOException {
        if (Files.exists(ASSIGNMENTS)) for (String line : Files.readAllLines(ASSIGNMENTS)) {
            String[] x = line.split("\\t", -1); if (x.length >= 6) {
                Assignment a = new Assignment(Integer.parseInt(x[0]), dec(x[1]), dec(x[2]), dec(x[3]), dec(x[4]), dec(x[5]));
                assignments.add(a); nextAssignmentId = Math.max(nextAssignmentId, a.id + 1);
            }
        }
        if (Files.exists(SUBMISSIONS)) for (String line : Files.readAllLines(SUBMISSIONS)) {
            String[] x = line.split("\\t", -1); if (x.length >= 8) {
                Submission s = new Submission(Integer.parseInt(x[0]), dec(x[1]), dec(x[2]), dec(x[3]), dec(x[4]), dec(x[5]), dec(x[6]), Integer.parseInt(x[7]));
                submissions.add(s); nextSubmissionId = Math.max(nextSubmissionId, s.id + 1);
            }
        }
    }
    private static String enc(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String s) { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8); }
    private static void saveData() {
        try {
            List<String> a = new ArrayList<>();
            for (Assignment x : assignments) a.add(x.id+"\t"+enc(x.title)+"\t"+enc(x.subject)+"\t"+enc(x.section)+"\t"+enc(x.dueDate)+"\t"+enc(x.instructions));
            Files.write(ASSIGNMENTS, a);
            List<String> s = new ArrayList<>();
            for (Submission x : submissions) s.add(x.id+"\t"+enc(x.student)+"\t"+enc(x.assignment)+"\t"+enc(x.answer)+"\t"+enc(x.date)+"\t"+enc(x.status)+"\t"+enc(x.feedback)+"\t"+x.marks);
            Files.write(SUBMISSIONS, s);
        } catch (IOException e) { e.printStackTrace(); }
    }
    static class Assignment {
        int id; String title, subject, section, dueDate, instructions;
        Assignment(int id,String title,String subject,String section,String dueDate,String instructions){this.id=id;this.title=title;this.subject=subject;this.section=section;this.dueDate=dueDate;this.instructions=instructions;}
        String json(){return "{\"id\":"+id+",\"title\":\""+esc(title)+"\",\"subject\":\""+esc(subject)+"\",\"section\":\""+esc(section)+"\",\"dueDate\":\""+esc(dueDate)+"\",\"instructions\":\""+esc(instructions)+"\"}";}
    }
    static class Submission {
        int id, marks; String student, assignment, answer, date, status, feedback;
        Submission(int id,String student,String assignment,String answer,String date,String status,String feedback,int marks){this.id=id;this.student=student;this.assignment=assignment;this.answer=answer;this.date=date;this.status=status;this.feedback=feedback;this.marks=marks;}
        String json(){return "{\"id\":"+id+",\"student\":\""+esc(student)+"\",\"assignment\":\""+esc(assignment)+"\",\"answer\":\""+esc(answer)+"\",\"date\":\""+esc(date)+"\",\"status\":\""+esc(status)+"\",\"feedback\":\""+esc(feedback)+"\",\"marks\":"+marks+"}";}
    }
}
