package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.FileStorageService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileStorageService files;

    public FileController(FileStorageService files) { this.files = files; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) {
        return files.upload(CurrentUser.id(), file);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<?> content(@PathVariable long id) {
        FileStorageService.StoredFile file = files.load(CurrentUser.id(), id);
        MediaType type;
        try { type = MediaType.parseMediaType(file.mimeType()); }
        catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(file.size())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.resource());
    }
}
