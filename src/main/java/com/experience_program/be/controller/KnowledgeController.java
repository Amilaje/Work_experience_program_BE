package com.experience_program.be.controller;

import com.experience_program.be.dto.KnowledgeRequestDto;
import com.experience_program.be.dto.KnowledgeUpdateDto;
import com.experience_program.be.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @Autowired
    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping
    public ResponseEntity<Void> registerKnowledge(@RequestBody KnowledgeRequestDto requestDto) {
        knowledgeService.registerKnowledge(requestDto);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public Mono<ResponseEntity<Object>> getAllKnowledge(
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "source_type", required = false) String sourceType,
            Pageable pageable) {
        return knowledgeService.getAllKnowledge(title, sourceType, pageable)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{knowledgeId}")
    public Mono<ResponseEntity<Object>> getKnowledgeById(@PathVariable String knowledgeId) {
        return knowledgeService.getKnowledgeById(knowledgeId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{knowledgeId}")
    public ResponseEntity<Void> updateKnowledge(@PathVariable String knowledgeId, @RequestBody KnowledgeUpdateDto requestDto) {
        knowledgeService.updateKnowledge(knowledgeId, requestDto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{knowledgeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKnowledge(@PathVariable String knowledgeId) {
        knowledgeService.deleteKnowledge(knowledgeId);
    }
}
