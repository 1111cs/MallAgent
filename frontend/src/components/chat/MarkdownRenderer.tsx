// @ts-nocheck
/* eslint-disable */

import * as React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkCjkFriendly from "remark-cjk-friendly";
import rehypeRaw from "rehype-raw";
import rehypeSanitize from "rehype-sanitize";
import { Check, Copy, ImageIcon } from "lucide-react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark, oneLight } from "react-syntax-highlighter/dist/esm/styles/prism";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useThemeStore } from "@/stores/themeStore";

interface MarkdownRendererProps {
  content: string;
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  const theme = useThemeStore((state) => state.theme);

  return (
    <ReactMarkdown
      remarkPlugins={[[remarkGfm, { singleTilde: false }], remarkCjkFriendly]}
      rehypePlugins={[rehypeRaw, rehypeSanitize]}
      components={{
        code({ inline, className, children, node, ...props }) {
          const match = /language-(\w+)/.exec(className || "");
          const language = match?.[1] || "text";
          const value = String(children).replace(/\n$/, "");

          // 判断是否为内联代码：inline 为 true 或者没有换行符
          if (inline || !value.includes('\n')) {
            return (
              <code
                className={cn(
                  "mx-0.5 rounded px-1.5 py-0.5 text-[13px] font-mono bg-[#f6f8fa] text-[#24292f]",
                  "dark:bg-[#161b22] dark:text-[#c9d1d9]",
                  className
                )}
                {...props}
              >
                {children}
              </code>
            );
          }

          return (
            <div className="my-3 overflow-hidden rounded-md border border-[#d0d7de] bg-[#f6f8fa] dark:border-[#30363d] dark:bg-[#161b22]">
              <div className="flex items-center justify-between border-b border-[#d0d7de] bg-[#f6f8fa] px-3 py-1.5 dark:border-[#30363d] dark:bg-[#161b22]">
                <span className="font-mono text-[11px] font-semibold uppercase tracking-wider text-[#57606a] dark:text-[#8b949e]">
                  {language}
                </span>
                <CopyButton value={value} />
              </div>
              <div className="overflow-x-auto">
                <SyntaxHighlighter
                  language={language}
                  style={theme === "dark" ? oneDark : oneLight}
                  PreTag="div"
                  customStyle={{
                    margin: 0,
                    padding: "0.75rem 1rem",
                    background: "transparent",
                    fontSize: "13px",
                    lineHeight: "1.5"
                  }}
                  showLineNumbers={false}
                  wrapLines={true}
                >
                  {value}
                </SyntaxHighlighter>
              </div>
            </div>
          );
        },
        img({ src, alt, ...props }) {
          const [hasError, setHasError] = React.useState(false);

          if (hasError) {
            return (
              <div className="my-3 flex items-center gap-2 text-sm text-[#999999]">
                <ImageIcon className="h-4 w-4" />
                <span>图片加载失败</span>
              </div>
            );
          }

          return (
            <img
              src={src}
              alt=""
              className="my-3 max-w-full rounded-lg"
              onError={() => setHasError(true)}
              loading="lazy"
              {...props}
            />
          );
        },
        a({ children, ...props }) {
          return (
            <a
              className="text-[#0969da] underline-offset-4 hover:underline dark:text-[#58a6ff]"
              target="_blank"
              rel="noreferrer"
              {...props}
            >
              {children}
            </a>
          );
        },
        h1({ children, ...props }) {
          return (
            <h1
              className="mt-6 mb-4 border-b border-[#d0d7de] pb-2 text-3xl font-bold leading-tight first:mt-0 dark:border-[#30363d]"
              {...props}
            >
              {children}
            </h1>
          );
        },
        h2({ children, ...props }) {
          return (
            <h2
              className="mt-6 mb-4 border-b border-[#d0d7de] pb-1.5 text-2xl font-bold leading-tight first:mt-0 dark:border-[#30363d]"
              {...props}
            >
              {children}
            </h2>
          );
        },
        h3({ children, ...props }) {
          return (
            <h3 className="mt-5 mb-3 text-xl font-bold leading-snug first:mt-0" {...props}>
              {children}
            </h3>
          );
        },
        h4({ children, ...props }) {
          return (
            <h4 className="mt-4 mb-2 text-base font-bold leading-snug first:mt-0" {...props}>
              {children}
            </h4>
          );
        },
        table({ children, ...props }) {
          return (
            <div className="my-6 w-full min-w-0 overflow-x-auto">
              <table
                className="w-full border-separate border-spacing-0 overflow-hidden rounded-lg border border-[#d0d7de] text-sm dark:border-[#30363d] [&_tr:last-child>td]:border-b-0"
                {...props}
              >
                {children}
              </table>
            </div>
          );
        },
        thead({ children, ...props }) {
          return (
            <thead className="bg-[#eaeef2] dark:bg-[#21262d]" {...props}>
              {children}
            </thead>
          );
        },
        tr({ children, ...props }) {
          return (
            <tr
              className="transition-colors hover:bg-[#f6f8fa]/60 dark:hover:bg-[#161b22]/60"
              {...props}
            >
              {children}
            </tr>
          );
        },
        th({ children, ...props }) {
          return (
            <th
              className="border-b border-r border-[#d0d7de] px-2 py-2 text-left text-sm font-semibold text-[#24292f] align-middle break-words last:border-r-0 dark:border-[#30363d] dark:text-[#c9d1d9]"
              {...props}
            >
              {children}
            </th>
          );
        },
        td({ children, ...props }) {
          return (
            <td
              className="border-b border-r border-[#d0d7de] px-2 py-2 text-sm text-[#24292f] align-middle break-words last:border-r-0 dark:border-[#30363d] dark:text-[#c9d1d9]"
              {...props}
            >
              {children}
            </td>
          );
        },
        blockquote({ children, ...props }) {
          return (
            <blockquote
              className="my-5 rounded-r-md border-l-4 border-[#0969da] bg-[#f6f8fa] px-6 py-4 italic text-[#24292f] dark:border-[#58a6ff] dark:bg-[#161b22] dark:text-[#c9d1d9] [&_p:first-of-type]:before:content-none [&_p:last-of-type]:after:content-none"
              {...props}
            >
              {children}
            </blockquote>
          );
        },
        ul({ children, ...props }) {
          return (
            <ul
              className="my-4 list-disc space-y-2 pl-6 marker:text-[#6e7781] dark:marker:text-[#8b949e] [&_ul]:my-2 [&_ol]:my-2"
              {...props}
            >
              {children}
            </ul>
          );
        },
        ol({ children, ...props }) {
          return (
            <ol
              className="my-4 list-decimal space-y-2 pl-6 marker:text-[#6e7781] dark:marker:text-[#8b949e] [&_ul]:my-2 [&_ol]:my-2"
              {...props}
            >
              {children}
            </ol>
          );
        },
        hr({ ...props }) {
          return <hr className="my-6 border-0 border-t border-[#d0d7de] dark:border-[#30363d]" {...props} />;
        }
      }}
      className="prose prose-gray max-w-none break-words leading-[1.6] dark:prose-invert prose-headings:text-[#1A1A1A] dark:prose-headings:text-[#EEEEEE] prose-p:text-[#333333] dark:prose-p:text-[#CCCCCC] prose-p:leading-relaxed prose-li:text-[#333333] dark:prose-li:text-[#CCCCCC] prose-strong:text-[#1A1A1A] dark:prose-strong:text-[#EEEEEE]"
    >
      {content}
    </ReactMarkdown>
  );
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = React.useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  };

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={handleCopy}
      aria-label="复制代码"
      className="h-7 w-7 hover:bg-[#eaeef2] dark:hover:bg-[#30363d] transition-colors"
    >
      {copied ? (
        <Check className="h-3.5 w-3.5 text-green-600 dark:text-green-400" />
      ) : (
        <Copy className="h-3.5 w-3.5 text-[#57606a] dark:text-[#8b949e]" />
      )}
    </Button>
  );
}
