package com.attendance.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.HashMap;

@Controller
public class AttendanceController {

    private final String API_BASE_URL = "http://localhost:5000/api";
    private final RestTemplate restTemplate = new RestTemplate();
    
    @GetMapping("/")
    public String index() {
        return "login";
    }
    
    @PostMapping("/login")
    public String login(@RequestParam String orgId, 
                       @RequestParam String password,
                       HttpSession session,
                       Model model) {
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("orgId", orgId);
        requestBody.put("password", password);
        
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                API_BASE_URL + "/login",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            if ((Boolean) response.getBody().get("success")) {
                session.setAttribute("orgId", orgId);
                session.setAttribute("password", password);
                return "redirect:/dashboard";
            } else {
                model.addAttribute("error", "Invalid credentials");
                return "login";
            }
        } catch (Exception e) {
            model.addAttribute("error", "Error connecting to server: " + e.getMessage());
            return "login";
        }
    }
    
    @GetMapping("/register-organization")
    public String registerOrganizationForm() {
        return "register-organization";
    }
    
    @PostMapping("/register-organization")
    public String registerOrganization(@RequestParam String orgId,
                                      @RequestParam String password,
                                      @RequestParam String orgName,
                                      Model model) {
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("orgId", orgId);
        requestBody.put("password", password);
        requestBody.put("orgName", orgName);
        
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                API_BASE_URL + "/register-organization",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            if ((Boolean) response.getBody().get("success")) {
                model.addAttribute("message", "Organization registered successfully");
                return "login";
            } else {
                model.addAttribute("error", response.getBody().get("message"));
                return "register-organization";
            }
        } catch (Exception e) {
            model.addAttribute("error", "Error connecting to server: " + e.getMessage());
            return "register-organization";
        }
    }
    
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (session.getAttribute("orgId") == null) {
            return "redirect:/";
        }
        
        model.addAttribute("orgId", session.getAttribute("orgId"));
        return "dashboard";
    }
    
    @GetMapping("/scan-face")
    public String scanFace(HttpSession session, Model model) {
        if (session.getAttribute("orgId") == null) {
            return "redirect:/";
        }
        
        model.addAttribute("orgId", session.getAttribute("orgId"));
        return "scan-face";
    }
    
    @PostMapping("/recognize-face")
    public String recognizeFace(@RequestParam("image") MultipartFile image,
                               HttpSession session,
                               Model model) {
        
        if (session.getAttribute("orgId") == null) {
            return "redirect:/";
        }
        
        String orgId = (String) session.getAttribute("orgId");
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("orgId", orgId);
            body.add("image", image.getResource());
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                API_BASE_URL + "/recognize-face",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            if ((Boolean) response.getBody().get("success")) {
                if ((Boolean) response.getBody().get("recognized")) {
                    model.addAttribute("recognized", true);
                    model.addAttribute("name", response.getBody().get("name"));
                    model.addAttribute("timestamp", response.getBody().get("timestamp"));
                } else {
                    model.addAttribute("recognized", false);
                    model.addAttribute("message", "Face not recognized");
                }
            } else {
                model.addAttribute("error", response.getBody().get("message"));
            }
            
        } catch (Exception e) {
            model.addAttribute("error", "Error connecting to server: " + e.getMessage());
        }
        
        return "recognition-result";
    }
    
    @GetMapping("/register-face")
    public String registerFace(HttpSession session, Model model) {
        if (session.getAttribute("orgId") == null) {
            return "redirect:/";
        }
        
        model.addAttribute("orgId", session.getAttribute("orgId"));
        return "register-face";
    }
    
    @PostMapping("/register-face")
    public String registerFace(@RequestParam("image") MultipartFile image,
                              @RequestParam("name") String name,
                              HttpSession session,
                              Model model) {
        
        if (session.getAttribute("orgId") == null) {
            return "redirect:/";
        }
        
        String orgId = (String) session.getAttribute("orgId");
        String password = (String) session.getAttribute("password");
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("orgId", orgId);
            body.add("password", password);
            body.add("name", name);
            body.add("image", image.getResource());
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                API_BASE_URL + "/register-face",
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            if ((Boolean) response.getBody().get("success")) {
                model.addAttribute("message", "Face registered successfully");
            } else {
                model.addAttribute("error", response.getBody().get("message"));
            }
            
        } catch (Exception e) {
            model.addAttribute("error", "Error connecting to server: " + e.getMessage());
        }
        
        return "register-face-result";
    }
    
    @GetMapping("/attendance-logs")
    public String attendanceLogs(@RequestParam(required = false) String date,
                                HttpSession session,
                                Model model) {
        
        if (session.getAttribute("orgId") == null) {
            return "redirect:/";
        }
        
        String orgId = (String) session.getAttribute("orgId");
        String password = (String) session.getAttribute("password");
        
        try {
            String url = API_BASE_URL + "/attendance-logs?orgId=" + orgId + 
                         "&password=" + password;
            
            if (date != null && !date.isEmpty()) {
                url += "&date=" + date;
            }
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if ((Boolean) response.getBody().get("success")) {
                model.addAttribute("logs", response.getBody().get("logs"));
            } else {
                model.addAttribute("error", response.getBody().get("message"));
            }
            
        } catch (Exception e) {
            model.addAttribute("error", "Error connecting to server: " + e.getMessage());
        }
        
        model.addAttribute("date", date);
        return "attendance-logs";
    }
    
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}