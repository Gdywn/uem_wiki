package edu.uemwiki.contrib.service;

import edu.uemwiki.contrib.entity.Contribution;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 投稿审核与发布的业务入口；文件持久化和发布细节由实现类处理。 */
public interface ContributionService {
    List<Contribution> list(String status);
    Contribution get(long id);
    Path file(Contribution contribution);
    long submit(String title, String course, String majorId, String body, String author, String contact, MultipartFile file) throws IOException;
    void review(long id, String decision);
    void publish(long id) throws IOException;
}