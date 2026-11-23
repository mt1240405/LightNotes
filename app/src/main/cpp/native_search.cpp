#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cctype>

static inline std::string to_lower(const std::string& str) {
    std::string lower = str;
    std::transform(lower.begin(), lower.end(), lower.begin(),
                   [](unsigned char c) { return std::tolower(c); });
    return lower;
}

static inline std::vector<std::string> split_words(const std::string& text) {
    std::vector<std::string> words;
    std::string current;
    for (char c : text) {
        if (std::isspace(static_cast<unsigned char>(c))) {
            if (!current.empty()) {
                words.push_back(current);
                current.clear();
            }
        } else {
            current.push_back(c);
        }
    }
    if (!current.empty()) {
        words.push_back(current);
    }
    return words;
}

struct NoteScore {
    int index;
    int score;
};

extern "C" JNIEXPORT jintArray JNICALL
Java_com_lightnotes_app_search_NativeSearchOptimizer_nativeRankNotes(
    JNIEnv* env,
    jobject /* this */,
    jstring j_query,
    jobjectArray j_titles,
    jobjectArray j_contents
) {
    if (!j_query || !j_titles || !j_contents) {
        return env->NewIntArray(0);
    }

    const char* query_chars = env->GetStringUTFChars(j_query, nullptr);
    std::string query_str(query_chars ? query_chars : "");
    if (query_chars) env->ReleaseStringUTFChars(j_query, query_chars);

    std::string query_lower = to_lower(query_str);
    std::vector<std::string> query_terms = split_words(query_lower);
    if (query_terms.empty()) {
        return env->NewIntArray(0);
    }

    jsize count = env->GetArrayLength(j_titles);
    std::vector<NoteScore> scored_notes;
    scored_notes.reserve(count);

    for (jsize i = 0; i < count; ++i) {
        auto j_title = static_cast<jstring>(env->GetObjectArrayElement(j_titles, i));
        auto j_content = static_cast<jstring>(env->GetObjectArrayElement(j_contents, i));

        std::string title_str;
        if (j_title) {
            const char* title_chars = env->GetStringUTFChars(j_title, nullptr);
            if (title_chars) {
                title_str = title_chars;
                env->ReleaseStringUTFChars(j_title, title_chars);
            }
            env->DeleteLocalRef(j_title);
        }

        std::string content_str;
        if (j_content) {
            const char* content_chars = env->GetStringUTFChars(j_content, nullptr);
            if (content_chars) {
                content_str = content_chars;
                env->ReleaseStringUTFChars(j_content, content_chars);
            }
            env->DeleteLocalRef(j_content);
        }

        std::string title_lower = to_lower(title_str);
        std::string content_lower = to_lower(content_str);

        int total_score = 0;
        bool all_terms_matched = true;

        for (const auto& term : query_terms) {
            bool term_matched = false;

            size_t title_pos = title_lower.find(term);
            if (title_pos != std::string::npos) {
                term_matched = true;
                if (title_pos == 0) {
                    total_score += 60; // Prefix match in title
                } else {
                    total_score += 35; // Substring in title
                }
            }

            size_t content_pos = content_lower.find(term);
            if (content_pos != std::string::npos) {
                term_matched = true;
                total_score += 15; // Occurrence in content
            }

            if (!term_matched) {
                all_terms_matched = false;
                break;
            }
        }

        if (all_terms_matched && total_score > 0) {
            // Bonus for matching full query exactly in title
            if (title_lower == query_lower) {
                total_score += 100;
            } else if (title_lower.find(query_lower) != std::string::npos) {
                total_score += 40;
            }
            scored_notes.push_back({static_cast<int>(i), total_score});
        }
    }

    // Sort descending by relevance score
    std::stable_sort(scored_notes.begin(), scored_notes.end(),
                     [](const NoteScore& a, const NoteScore& b) {
                         return a.score > b.score;
                     });

    jintArray result = env->NewIntArray(static_cast<jsize>(scored_notes.size()));
    if (!result) return env->NewIntArray(0);

    std::vector<jint> indices;
    indices.reserve(scored_notes.size());
    for (const auto& item : scored_notes) {
        indices.push_back(item.index);
    }

    env->SetIntArrayRegion(result, 0, static_cast<jsize>(indices.size()), indices.data());
    return result;
}
