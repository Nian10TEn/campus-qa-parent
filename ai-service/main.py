from fastapi import FastAPI
from pydantic import BaseModel
from langchain_community.document_loaders import TextLoader
from langchain_text_splitters import CharacterTextSplitter
from langchain_community.vectorstores import FAISS
from langchain_community.embeddings.fastembed import FastEmbedEmbeddings
import os
import requests

app = FastAPI(title="校园客服 AI 服务")

class QuestionRequest(BaseModel):
    question: str
    history: list = []

# 全局变量
vector_store = None
DEEPSEEK_API_KEY = "api key"
DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"

def init_vector_store():
    global vector_store
    faq_path = os.path.join(os.path.dirname(__file__), "faq.txt")
    if not os.path.exists(faq_path):
        print("警告：未找到 faq.txt")
        return

    loader = TextLoader(faq_path, encoding="utf-8")
    docs = loader.load()
    text_splitter = CharacterTextSplitter(separator="\n", chunk_size=200, chunk_overlap=0)
    split_docs = text_splitter.split_documents(docs)

    # 使用 fastembed，纯 ONNX，不依赖 PyTorch
    embeddings = FastEmbedEmbeddings(model_name="BAAI/bge-small-zh-v1.5")

    vector_store = FAISS.from_documents(split_docs, embeddings)
    print(f"FAISS 向量库构建完成，共 {len(split_docs)} 条知识")

@app.post("/ask")
async def ask_question(request: QuestionRequest):
    if not vector_store:
        return {"answer": "知识库尚未初始化"}

    question = request.question
    docs = vector_store.similarity_search(question, k=3)
    context = "\n".join([d.page_content for d in docs])

    history_text = ""
    for msg in request.history:
        if msg.get("role") == "user":
            history_text += f"用户：{msg['content']}\n"
        elif msg.get("role") == "assistant":
            history_text += f"客服：{msg['content']}\n"

    prompt = f"""你是一个校园客服助手，请根据以下知识库内容回答用户问题。
如果知识库中没有相关信息，请礼貌地建议转人工客服。

知识库：
{context}

对话历史：
{history_text}
用户：{question}
客服："""

    try:
        headers = {
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": "deepseek-chat",
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.7,
            "max_tokens": 500
        }
        resp = requests.post(DEEPSEEK_API_URL, headers=headers, json=payload, timeout=30)
        if resp.status_code == 200:
            answer = resp.json()["choices"][0]["message"]["content"]
        else:
            answer = "AI 服务暂时不可用，请稍后重试。"
    except Exception as e:
        print(f"调用 DeepSeek 失败: {e}")
        answer = "抱歉，回答生成失败，请稍后再试。"

    return {"answer": answer}

@app.on_event("startup")
async def startup_event():
    init_vector_store()