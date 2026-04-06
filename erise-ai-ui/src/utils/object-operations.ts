/**
 * 通用对象操作工具
 */

import { ElMessage, ElMessageBox } from "element-plus";

/**
 * 确认删除对话框
 */
export const confirmDelete = async (
  itemName: string,
  itemType: string = "项目",
) => {
  try {
    await ElMessageBox.confirm(
      `确认删除"${itemName}"吗？此操作不可恢复。`,
      `删除${itemType}`,
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning",
        confirmButtonClass: "el-button--danger",
      },
    );
    return true;
  } catch {
    return false;
  }
};

/**
 * 导出文档为特定格式
 */
export const exportDocument = async (
  documentId: number,
  format: "pdf" | "docx" | "md" = "pdf",
  documentTitle: string = "document",
) => {
  try {
    // 构建导出 URL
    const exportUrl = `/api/v1/documents/${documentId}/export?format=${format}`;

    // 创建临时下载链接
    const link = document.createElement("a");
    link.href = exportUrl;
    link.download = `${documentTitle}.${format === "docx" ? "docx" : format === "pdf" ? "pdf" : "md"}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    ElMessage.success(`文档已导出为 ${format.toUpperCase()} 格式`);
  } catch (error) {
    ElMessage.error("导出失败，请稍后重试");
    console.error("Export error:", error);
  }
};

/**
 * 复制文本到剪贴板
 */
export const copyToClipboard = async (text: string, name: string = "链接") => {
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text);
      ElMessage.success(`已复制${name}`);
    } else {
      // 降级方案
      const textArea = document.createElement("textarea");
      textArea.value = text;
      document.body.appendChild(textArea);
      textArea.select();
      document.execCommand("copy");
      document.body.removeChild(textArea);
      ElMessage.success(`已复制${name}`);
    }
  } catch (error) {
    ElMessage.error(`复制${name}失败`);
  }
};

/**
 * 生成分享链接
 */
export const generateShareLink = (itemType: string, itemId: number) => {
  const baseUrl = window.location.origin;
  return `${baseUrl}/${itemType}/${itemId}/share`;
};
